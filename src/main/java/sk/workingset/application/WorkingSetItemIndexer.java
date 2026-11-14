package sk.workingset.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.SavepointManager;

import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.TrackerItemCatalog;
import sk.workingset.application.port.TrackerItemReadPort;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetCreation.Failure;
import sk.workingset.domain.WorkingSetCreation.IndexProgress;
import sk.workingset.domain.WorkingSetCreation.ItemOutcome;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetId;

/** Indexes one bounded page under the creation task's transaction. */
public final class WorkingSetItemIndexer {
    private static final Logger log = LoggerFactory.getLogger(WorkingSetItemIndexer.class);
    private static final Comparator<String> LOCAL_ID_ORDER = (left, right) ->
            Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));

    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;
    private final TrackerItemCatalog catalog;
    private final BusinessTrackerItems businessItems;
    private final Function<LocalId, TrackerItemReadPort> readPorts;
    private final IdentifierGenerator ids;
    private final int batchSize;

    public WorkingSetItemIndexer(WorkingSetRepository workingSets, TrackerRepository trackers,
                                  LightTrackerItemRepository items, TrackerItemCatalog catalog,
                                  BusinessTrackerItems businessItems, Function<LocalId, TrackerItemReadPort> readPorts,
                                  IdentifierGenerator ids, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Index batch size must be positive");
        }
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.items = items;
        this.catalog = catalog;
        this.businessItems = businessItems;
        this.readPorts = readPorts;
        this.ids = ids;
        this.batchSize = batchSize;
    }

    WorkingSetCreation indexBatch(WorkingSetCreation creation, SavepointManager transaction) {
        var parent = TrackerId.of(creation.currentItem());
        var target = WorkingSetId.of(creation.request().newWorkingSetId());
        if (creation.failures().stream().anyMatch(failure -> parent.value().equals(failure.trackerId())
                && (failure.stage() == Stage.COPY_UNSHARED_TRACKERS || failure.stage() == Stage.BIND_UNSHARED_TRACKERS))) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        var child = trackers.findDerivedTracker(target, parent).orElse(null);
        var targetSet = workingSets.lockWorkingSetForCreation(target).orElseThrow();
        if (child == null || !targetSet.hasTrackerBinding(child.trackerId())) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        var progress = creation.indexProgress().getOrDefault(parent.value(), IndexProgress.initial());
        var savepoint = transaction.createSavepoint();
        List<Candidate> page;
        try {
            page = page(parent, progress.afterLocalId());
        } catch (RuntimeException failure) {
            transaction.rollbackToSavepoint(savepoint);
            return creation.advance(ItemOutcome.Failed, failure(parent, child, null, reason(failure)), Instant.now());
        } finally {
            transaction.releaseSavepoint(savepoint);
        }

        var failures = new ArrayList<Failure>();
        int succeeded = progress.succeeded();
        int skipped = progress.skipped();
        for (Candidate candidate : page) {
            var itemSavepoint = transaction.createSavepoint();
            try {
                switch (index(parent, child, candidate)) {
                    case Indexed -> succeeded++;
                    case Existing -> skipped++;
                    case Excluded -> { }
                }
            } catch (RuntimeException failure) {
                transaction.rollbackToSavepoint(itemSavepoint);
                failures.add(failure(parent, child, candidate.localId(), reason(failure)));
            } finally {
                transaction.releaseSavepoint(itemSavepoint);
            }
        }
        boolean finished = page.size() < batchSize;
        String cursor = page.isEmpty() ? progress.afterLocalId() : page.getLast().localId().value();
        return creation.recordIndexBatch(new IndexProgress(cursor, succeeded, progress.failed() + failures.size(),
                skipped, finished ? child.trackerId().value() : null), failures, finished, Instant.now());
    }

    private List<Candidate> page(TrackerId parent, String cursor) {
        var candidates = new TreeMap<String, Candidate>(LOCAL_ID_ORDER);
        items.pageInTracker(parent, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        catalog.page(parent, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        return candidates.values().stream().limit(batchSize).toList();
    }

    private IndexResult index(TrackerId parent, Tracker child, Candidate candidate) {
        if (candidate.lightweight() && catalog.hasActiveFamily(parent, candidate.commonId())) {
            return IndexResult.Excluded;
        }
        if (!businessItems.lockContentSource(candidate.sourceId())) {
            throw new ApplicationFailure(FailureReason.NOT_FOUND, candidate.sourceId().value());
        }
        var reader = readPorts.apply(candidate.sourceId());
        try {
            reader.readInheritedAt(candidate.sourceRevision());
        } catch (ApplicationFailure failure) {
            if (failure.reason() == FailureReason.DELETED) {
                return IndexResult.Excluded;
            }
            throw failure;
        }
        if (items.hasFamily(child.workingSetId(), candidate.commonId())) {
            return IndexResult.Existing;
        }
        LocalId id;
        do {
            id = LocalId.of(Long.toString(ids.nextId()));
        } while (id.value().equals(candidate.commonId().value()) || id.equals(candidate.sourceId()));
        var item = LightTrackerItem.derive(id, candidate.commonId(), child.trackerId(), child.workingSetId(),
                candidate.revision(), candidate.sourceId(), candidate.sourceRevision());
        return items.insertIfFamilyAbsent(item) ? IndexResult.Indexed : IndexResult.Existing;
    }

    private static Failure failure(TrackerId parent, Tracker child, LocalId localId, FailureReason reason) {
        return new Failure(Stage.INDEX_UNSHARED_TRACKERS, child.productSpaceId(), parent.value(),
                localId == null ? null : localId.value(), reason.name(), Instant.now());
    }

    private static FailureReason reason(RuntimeException failure) {
        if (failure instanceof ApplicationFailure applicationFailure) {
            return applicationFailure.reason();
        }
        log.warn("Working set indexing operation failed", failure);
        return failure instanceof DataAccessResourceFailureException
                ? FailureReason.DEPENDENCY_UNAVAILABLE : FailureReason.UNKNOWN;
    }

    private enum IndexResult { Indexed, Existing, Excluded }

    private record Candidate(LocalId localId, CommonId commonId, Revision revision,
                             LocalId sourceId, Revision sourceRevision, boolean lightweight) {
        static Candidate from(LightTrackerItem item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(),
                    item.effectiveContentSource(), item.effectiveContentRevision(), true);
        }

        static Candidate from(TrackerItemCatalog.Identity item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(), item.localId(), item.revision(), false);
        }
    }
}
