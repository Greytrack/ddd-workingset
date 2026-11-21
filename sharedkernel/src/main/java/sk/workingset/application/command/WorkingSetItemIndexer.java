package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.SavepointManager;
import sk.RequestThreadHolder;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.BusinessObjectTypeAdapter;
import sk.workingset.application.query.provider.*;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.lighttrackeritem.ItemClassification;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetCreation;
import sk.workingset.domain.workingset.WorkingSetCreation.Failure;
import sk.workingset.domain.workingset.WorkingSetCreation.IndexProgress;
import sk.workingset.domain.workingset.WorkingSetCreation.ItemOutcome;
import sk.workingset.domain.workingset.WorkingSetCreation.Stage;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Pages parent Items using the baseline time stored with the creation task.
 *
 * <p>所有跨 BC 读取都经由 {@link BusinessObjectTypeRegistry} 按父 Tracker 路由；SK 不直接
 * 依赖任何业务 BC 的仓储或表语义。
 */
@Slf4j
public final class WorkingSetItemIndexer {

    static final long FOLDER_CLASSIFICATION_ID = 10070101L;
    private static final Comparator<String> LOCAL_ID_ORDER = Comparator.comparingLong(Long::parseLong);
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemQueryProvider itemQueries;
    private final LightTrackerItemRepository items;
    private final BusinessObjectTypeRegistry registry;
    private final IdentifierGenerator ids;
    private final int batchSize;

    public WorkingSetItemIndexer(WorkingSetRepository workingSets, TrackerRepository trackers,
                                 LightTrackerItemQueryProvider itemQueries,
                                 LightTrackerItemRepository items,
                                 BusinessObjectTypeRegistry registry,
                                 IdentifierGenerator ids, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Index batch size must be positive");
        }
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.itemQueries = itemQueries;
        this.items = items;
        this.registry = registry;
        this.ids = ids;
        this.batchSize = batchSize;
    }

    private static Outcome skipped(TrackerId parent, Tracker child, Candidate candidate, FailureReason reason) {
        return new Outcome(IndexResult.Skipped, failure(parent, child, candidate.localId(), reason));
    }

    private static Failure failure(TrackerId parent, Tracker child, LocalId localId, FailureReason reason) {
        return new Failure(Stage.INDEX_UNSHARED_TRACKERS, child.productSpaceId(), parent.value(),
                localId == null ? null : localId.value(), reason.name(), System.currentTimeMillis());
    }

    private static FailureReason reason(RuntimeException failure) {
        if (failure instanceof DomainException domainFailure) {
            return FailureReason.of(domainFailure);
        }
        log.warn("Working set indexing operation failed", failure);
        return failure instanceof DataAccessResourceFailureException
                ? FailureReason.DEPENDENCY_UNAVAILABLE : FailureReason.UNKNOWN;
    }

    private static ItemClassification classificationOf(Tracker tracker) {
        return new ItemClassification(tracker.numericProductSpaceId(), tracker.numericClassificationId());
    }

    /**
     * 按当前 Tracker 的基线时间分页索引 Item，返回推进后的任务进度。
     */
    public WorkingSetCreation indexBatch(WorkingSetCreation creation, SavepointManager transaction) {
        TrackerId parent = TrackerId.of(creation.currentItem());
        WorkingSetId target = WorkingSetId.of(creation.request().newWorkingSetId());
        if (creation.failures().stream().anyMatch(failure -> parent.value().equals(failure.trackerId())
                && (failure.stage() == Stage.COPY_UNSHARED_TRACKERS || failure.stage() == Stage.BIND_PRODUCT_AND_TRACKER))) {
            return creation.advance(ItemOutcome.Skipped, null, System.currentTimeMillis());
        }
        Tracker child = trackers.findDerivedTracker(target, parent).orElse(null);
        WorkingSet targetSet = workingSets.findByIdForCreation(target).orElseThrow();
        if (child == null || !targetSet.hasTrackerBinding(child.trackerId())) {
            return creation.advance(ItemOutcome.Skipped, null, System.currentTimeMillis());
        }
        long baselineAt = creation.baselineAt(parent.value());
        WorkingSetCreation.IndexProgress progress = creation.indexProgress().getOrDefault(parent.value(), IndexProgress.initial());
        Object savepoint = transaction.createSavepoint();
        BusinessObjectTypeAdapter adapter = null;
        List<Candidate> page;
        try {
            adapter = registry.forTracker(parent);
            if (!adapter.capabilities().derivable()) {
                throw DomainException.withMessage(FailureReason.CAPABILITY_NOT_SUPPORTED, parent.value());
            }
            page = livePage(parent, baselineAt, progress.afterLocalId(), adapter);
        } catch (RuntimeException failure) {
            transaction.rollbackToSavepoint(savepoint);
            FailureReason reason = reason(failure);
            // The default Folder Tracker can exist before a Folder business catalog is installed.
            if (adapter == null && reason == FailureReason.CAPABILITY_NOT_SUPPORTED
                    && child.numericClassificationId() == FOLDER_CLASSIFICATION_ID && !itemQueries.existsInTracker(parent)) {
                return creation.recordIndexBatch(new IndexProgress(progress.afterLocalId(), progress.succeeded(),
                        progress.failed(), progress.skipped(), child.trackerId().value()), List.of(), true, System.currentTimeMillis());
            }
            return creation.advance(ItemOutcome.Failed, failure(parent, child, null, reason), System.currentTimeMillis());
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
                WorkingSetItemIndexer.Outcome outcome = index(parent, child, candidate, adapter, baselineAt);
                switch (outcome.result()) {
                    case Indexed -> succeeded++;
                    case Existing -> skipped++;
                    case Skipped -> {
                        skipped++;
                        failures.add(outcome.failure());
                    }
                    case Excluded -> {
                    }
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
                finished ? child.trackerId().value() : null), failures, finished, System.currentTimeMillis());
    }

    private List<Candidate> livePage(TrackerId parent, long baselineAt, String cursor, BusinessObjectTypeAdapter adapter) {
        TreeMap<String, WorkingSetItemIndexer.Candidate> candidates = new TreeMap<String, Candidate>(LOCAL_ID_ORDER);
        itemQueries.pageInTracker(parent, baselineAt, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        adapter.catalog().page(parent, baselineAt, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        return candidates.values().stream().limit(batchSize).toList();
    }

    private Outcome index(TrackerId parent, Tracker child, Candidate candidate, BusinessObjectTypeAdapter adapter, long baselineAt) {
        // 重入/重复执行时先认已提交的同族，避免来源后来软删或不可读被误记为失败。
        if (itemQueries.hasFamily(child.workingSetId(), candidate.commonId())) {
            return new Outcome(IndexResult.Existing, null);
        }
        if (candidate.lightweight() && adapter.catalog().hasActiveFamily(parent, candidate.commonId(), baselineAt)) {
            return new Outcome(IndexResult.Excluded, null);
        }
        if (!adapter.sourceLock().lock(candidate.sourceId())) {
            return skipped(parent, child, candidate, FailureReason.NOT_FOUND);
        }
        TrackerItemQueryProvider reader = adapter.query(candidate.sourceId());
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
                candidate.revision(), candidate.sourceId(), candidate.sourceRevision(), classificationOf(child),
                RequestThreadHolder.getUserId());
        if (!items.claimFamily(item)) {
            return new Outcome(IndexResult.Existing, null);
        }
        return new Outcome(IndexResult.Indexed, null);
    }

    private enum IndexResult {Indexed, Existing, Skipped, Excluded}

    private record Outcome(IndexResult result, Failure failure) {
    }

    private record Candidate(LocalId localId, CommonId commonId, Revision revision,
                             LocalId sourceId, Revision sourceRevision, boolean lightweight) {
        static Candidate from(LightTrackerItemSummary item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(),
                    item.effectiveContentSource(), item.effectiveContentRevision(), true);
        }

        static Candidate from(TrackerItemCatalogQueryProvider.Identity item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(), item.localId(), item.revision(), false);
        }

    }
}
