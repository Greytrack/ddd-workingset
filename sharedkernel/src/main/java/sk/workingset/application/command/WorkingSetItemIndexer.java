package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.SavepointManager;

import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import sk.workingset.application.query.provider.TrackerItemQueryProvider;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetCreationSnapshotRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
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

/** Indexes the frozen parent-item snapshot under the creation task's transaction. */
public final class WorkingSetItemIndexer {
    private static final Logger log = LoggerFactory.getLogger(WorkingSetItemIndexer.class);
    // IDs are positive Snowflake longs; SQL snapshots and catalog pages use numeric order.
    private static final Comparator<String> LOCAL_ID_ORDER =
            Comparator.comparingLong(Long::parseLong);

    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;
    private final TrackerItemCatalogQueryProvider catalog;
    private final WorkingSetCreationSnapshotRepository snapshots;
    private final BusinessTrackerItems businessItems;
    private final Function<LocalId, TrackerItemQueryProvider> readPorts;
    private final IdentifierGenerator ids;
    private final int batchSize;

    public WorkingSetItemIndexer(WorkingSetRepository workingSets, TrackerRepository trackers,
                                 LightTrackerItemRepository items, TrackerItemCatalogQueryProvider catalog,
                                 WorkingSetCreationSnapshotRepository snapshots, BusinessTrackerItems businessItems,
                                 Function<LocalId, TrackerItemQueryProvider> readPorts,
                                 IdentifierGenerator ids, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Index batch size must be positive");
        }
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.items = items;
        this.catalog = catalog;
        this.snapshots = snapshots;
        this.businessItems = businessItems;
        this.readPorts = readPorts;
        this.ids = ids;
        this.batchSize = batchSize;
    }

    /**
     * 在受理事务内冻结父 Tracker 的 Item 身份与有效来源。软删项不进入快照；缺失或版次
     * 不可读的身份仍保留，由索引执行时记录跳过明细。
     */
    public void snapshot(WorkingSetCreation creation) {
        List<String> parents = creation.request().unsharedParentTrackerIds();
        if (parents.isEmpty()) {
            snapshots.save(creation.id(), List.of());
            return;
        }
        for (String parent : parents) {
            TrackerId parentTracker = TrackerId.of(parent);
            ArrayList<WorkingSetCreationSnapshotRepository.Item> frozen = new ArrayList<WorkingSetCreationSnapshotRepository.Item>();
            String cursor = "";
            while (true) {
                List<WorkingSetItemIndexer.Candidate> candidates = livePage(parentTracker, cursor);
                for (Candidate candidate : candidates) {
                    if (candidate.lightweight()
                            && (businessItems.isDeleted(candidate.sourceId())
                            || catalog.hasActiveFamily(parentTracker, candidate.commonId()))) {
                        continue;
                    }
                    frozen.add(new WorkingSetCreationSnapshotRepository.Item(parent, candidate.localId(),
                            candidate.commonId(), candidate.revision(), candidate.sourceId(),
                            candidate.sourceRevision()));
                }
                if (candidates.size() < batchSize) {
                    break;
                }
                cursor = candidates.getLast().localId().value();
            }
            snapshots.save(creation.id(), frozen);
        }
    }

    public WorkingSetCreation indexBatch(WorkingSetCreation creation, SavepointManager transaction) {
        TrackerId parent = TrackerId.of(creation.currentItem());
        WorkingSetId target = WorkingSetId.of(creation.request().newWorkingSetId());
        if (creation.failures().stream().anyMatch(failure -> parent.value().equals(failure.trackerId())
                && (failure.stage() == Stage.COPY_UNSHARED_TRACKERS || failure.stage() == Stage.BIND_UNSHARED_TRACKERS))) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        Tracker child = trackers.findDerivedTracker(target, parent).orElse(null);
        sk.workingset.domain.WorkingSet targetSet = workingSets.lockWorkingSetForCreation(target).orElseThrow();
        if (child == null || !targetSet.hasTrackerBinding(child.trackerId())) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        WorkingSetCreation.IndexProgress progress = creation.indexProgress().getOrDefault(parent.value(), IndexProgress.initial());
        Object savepoint = transaction.createSavepoint();
        List<Candidate> page;
        try {
            page = page(creation, parent, progress.afterLocalId());
        } catch (RuntimeException failure) {
            transaction.rollbackToSavepoint(savepoint);
            return creation.advance(ItemOutcome.Failed, failure(parent, child, null, reason(failure)), Instant.now());
        } finally {
            transaction.releaseSavepoint(savepoint);
        }

        ArrayList<WorkingSetCreation.Failure> failures = new ArrayList<Failure>();
        int succeeded = progress.succeeded();
        int skipped = progress.skipped();
        int failed = progress.failed();
        for (Candidate candidate : page) {
            Object itemSavepoint = transaction.createSavepoint();
            try {
                WorkingSetItemIndexer.Outcome outcome = index(parent, child, candidate);
                switch (outcome.result()) {
                    case Indexed -> succeeded++;
                    case Existing -> skipped++;
                    case Skipped -> {
                        skipped++;
                        failures.add(outcome.failure());
                    }
                    case Excluded -> { }
                }
            } catch (RuntimeException failure) {
                transaction.rollbackToSavepoint(itemSavepoint);
                failed++;
                failures.add(failure(parent, child, candidate.localId(), reason(failure)));
            } finally {
                transaction.releaseSavepoint(itemSavepoint);
            }
        }
        boolean finished = page.size() < batchSize;
        String cursor = page.isEmpty() ? progress.afterLocalId() : page.getLast().localId().value();
        return creation.recordIndexBatch(new IndexProgress(cursor, succeeded, failed, skipped,
                finished ? child.trackerId().value() : null), failures, finished, Instant.now());
    }

    private List<Candidate> page(WorkingSetCreation creation, TrackerId parent, String cursor) {
        if (snapshots.contains(creation.id())) {
            return snapshots.page(creation.id(), parent.value(), cursor, batchSize).stream()
                    .map(Candidate::from).toList();
        }
        return livePage(parent, cursor);
    }

    private List<Candidate> livePage(TrackerId parent, String cursor) {
        TreeMap<String,WorkingSetItemIndexer.Candidate> candidates = new TreeMap<String, Candidate>(LOCAL_ID_ORDER);
        items.pageInTracker(parent, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        catalog.page(parent, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        return candidates.values().stream().limit(batchSize).toList();
    }

    private Outcome index(TrackerId parent, Tracker child, Candidate candidate) {
        // 重入/重复执行时先认已提交的同族，避免来源后来软删或不可读被误记为失败。
        if (items.hasFamily(child.workingSetId(), candidate.commonId())) {
            return new Outcome(IndexResult.Existing, null);
        }
        if (candidate.lightweight() && catalog.hasActiveFamily(parent, candidate.commonId())) {
            return new Outcome(IndexResult.Excluded, null);
        }
        if (!businessItems.lockContentSource(candidate.sourceId())) {
            return skipped(parent, child, candidate, FailureReason.NOT_FOUND);
        }
        TrackerItemQueryProvider reader = readPorts.apply(candidate.sourceId());
        try {
            reader.readInheritedAt(candidate.sourceRevision());
        } catch (DomainException failure) {
            FailureReason reason = FailureReason.of(failure);
            if (reason == FailureReason.DELETED || reason == FailureReason.NOT_FOUND || reason == FailureReason.HISTORY_UNAVAILABLE) {
                return skipped(parent, child, candidate, reason);
            }
            throw failure;
        }
        LocalId id;
        do {
            id = LocalId.of(Long.toString(ids.nextId(null).longValue()));
        } while (id.value().equals(candidate.commonId().value()) || id.equals(candidate.sourceId()));
        LightTrackerItem item = LightTrackerItem.derive(id, candidate.commonId(), child.trackerId(), child.workingSetId(),
                candidate.revision(), candidate.sourceId(), candidate.sourceRevision());
        return items.insertIfFamilyAbsent(item)
                ? new Outcome(IndexResult.Indexed, null)
                : new Outcome(IndexResult.Existing, null);
    }

    private static Outcome skipped(TrackerId parent, Tracker child, Candidate candidate, FailureReason reason) {
        return new Outcome(IndexResult.Skipped, failure(parent, child, candidate.localId(), reason));
    }

    private static Failure failure(TrackerId parent, Tracker child, LocalId localId, FailureReason reason) {
        return new Failure(Stage.INDEX_UNSHARED_TRACKERS, child.productSpaceId(), parent.value(),
                localId == null ? null : localId.value(), reason.name(), Instant.now());
    }

    private static FailureReason reason(RuntimeException failure) {
        if (failure instanceof DomainException domainFailure) {
            return FailureReason.of(domainFailure);
        }
        log.warn("Working set indexing operation failed", failure);
        return failure instanceof DataAccessResourceFailureException
                ? FailureReason.DEPENDENCY_UNAVAILABLE : FailureReason.UNKNOWN;
    }

    private enum IndexResult { Indexed, Existing, Skipped, Excluded }

    private record Outcome(IndexResult result, Failure failure) {}

    private record Candidate(LocalId localId, CommonId commonId, Revision revision,
                             LocalId sourceId, Revision sourceRevision, boolean lightweight) {
        static Candidate from(LightTrackerItem item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(),
                    item.effectiveContentSource(), item.effectiveContentRevision(), true);
        }

        static Candidate from(TrackerItemCatalogQueryProvider.Identity item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(), item.localId(), item.revision(), false);
        }

        static Candidate from(WorkingSetCreationSnapshotRepository.Item item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(),
                    item.sourceLocalId(), item.sourceRevision(), false);
        }
    }
}
