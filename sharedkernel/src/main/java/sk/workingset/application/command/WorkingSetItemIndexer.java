package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.SavepointManager;
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
import sk.workingset.domain.workingset.*;
import sk.workingset.domain.workingset.WorkingSetCreation.Failure;
import sk.workingset.domain.workingset.WorkingSetCreation.IndexProgress;
import sk.workingset.domain.workingset.WorkingSetCreation.ItemOutcome;
import sk.workingset.domain.workingset.WorkingSetCreation.Stage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Indexes the frozen parent-item snapshot under the creation task's transaction.
 *
 * <p>所有跨 BC 读取都经由 {@link BusinessObjectTypeRegistry} 按父 Tracker 路由；SK 不直接
 * 依赖任何业务 BC 的仓储或表语义。
 */
@Slf4j
public final class WorkingSetItemIndexer {

    private static final Comparator<String> LOCAL_ID_ORDER = Comparator.comparingLong(Long::parseLong);

    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemQueryProvider itemQueries;
    private final LightTrackerItemRepository items;
    private final WorkingSetCreationSnapshotStore snapshots;
    private final BusinessObjectTypeRegistry registry;
    private final IdentifierGenerator ids;
    private final int batchSize;

    public WorkingSetItemIndexer(WorkingSetRepository workingSets, TrackerRepository trackers,
                                 LightTrackerItemQueryProvider itemQueries,
                                 LightTrackerItemRepository items,
                                 WorkingSetCreationSnapshotStore snapshots,
                                 BusinessObjectTypeRegistry registry,
                                 IdentifierGenerator ids, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Index batch size must be positive");
        }
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.itemQueries = itemQueries;
        this.items = items;
        this.snapshots = snapshots;
        this.registry = registry;
        this.ids = ids;
        this.batchSize = batchSize;
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

    private static FailureReason unsupportedEvidence() {
        return FailureReason.CAPABILITY_NOT_SUPPORTED;
    }

    private static ItemClassification classificationOf(Tracker tracker) {
        return new ItemClassification(tracker.numericProductSpaceId(), tracker.numericClassificationId());
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
            BusinessObjectTypeAdapter adapter = registry.forTracker(parentTracker);
            if (!adapter.capabilities().derivable()) {
                throw DomainException.withMessage(FailureReason.CAPABILITY_NOT_SUPPORTED,
                        "Tracker type " + adapter.types() + " does not support derivation");
            }
            ArrayList<WorkingSetCreationSnapshotStore.Item> frozen = new ArrayList<WorkingSetCreationSnapshotStore.Item>();
            String cursor = "";
            while (true) {
                List<WorkingSetItemIndexer.Candidate> candidates = livePage(parentTracker, cursor, adapter);
                for (Candidate candidate : candidates) {
                    if (candidate.lightweight()
                            && (isDeleted(adapter, candidate.sourceId())
                            || adapter.catalog().hasActiveFamily(parentTracker, candidate.commonId()))) {
                        continue;
                    }
                    frozen.add(new WorkingSetCreationSnapshotStore.Item(parent, candidate.localId(),
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

    /**
     * 按当前阶段项分页索引父 Tracker 的冻结 Item，返回推进后的任务进度。
     */
    public WorkingSetCreation indexBatch(WorkingSetCreation creation, SavepointManager transaction) {
        TrackerId parent = TrackerId.of(creation.currentItem());
        WorkingSetId target = WorkingSetId.of(creation.request().newWorkingSetId());
        if (creation.failures().stream().anyMatch(failure -> parent.value().equals(failure.trackerId())
                && (failure.stage() == Stage.COPY_UNSHARED_TRACKERS || failure.stage() == Stage.BIND_PRODUCT_AND_TRACKER))) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        Tracker child = trackers.findDerivedTracker(target, parent).orElse(null);
        WorkingSet targetSet = workingSets.findByIdForCreation(target).orElseThrow();
        if (child == null || !targetSet.hasTrackerBinding(child.trackerId())) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        BusinessObjectTypeAdapter adapter = registry.forTracker(parent);
        WorkingSetCreation.IndexProgress progress = creation.indexProgress().getOrDefault(parent.value(), IndexProgress.initial());
        Object savepoint = transaction.createSavepoint();
        List<Candidate> page;
        try {
            page = page(creation, parent, progress.afterLocalId(), adapter);
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
                WorkingSetItemIndexer.Outcome outcome = index(parent, child, candidate, adapter);
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
                finished ? child.trackerId().value() : null), failures, finished, Instant.now());
    }

    private List<Candidate> page(WorkingSetCreation creation, TrackerId parent, String cursor,
                                 BusinessObjectTypeAdapter adapter) {
        if (snapshots.contains(creation.id())) {
            return snapshots.page(creation.id(), parent.value(), cursor, batchSize).stream()
                    .map(Candidate::from).toList();
        }
        return livePage(parent, cursor, adapter);
    }

    private List<Candidate> livePage(TrackerId parent, String cursor, BusinessObjectTypeAdapter adapter) {
        TreeMap<String, WorkingSetItemIndexer.Candidate> candidates = new TreeMap<String, Candidate>(LOCAL_ID_ORDER);
        itemQueries.pageInTracker(parent, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        adapter.catalog().page(parent, cursor, batchSize).forEach(item ->
                candidates.put(item.localId().value(), Candidate.from(item)));
        return candidates.values().stream().limit(batchSize).toList();
    }

    private Outcome index(TrackerId parent, Tracker child, Candidate candidate, BusinessObjectTypeAdapter adapter) {
        // 重入/重复执行时先认已提交的同族，避免来源后来软删或不可读被误记为失败。
        if (itemQueries.hasFamily(child.workingSetId(), candidate.commonId())) {
            return new Outcome(IndexResult.Existing, null);
        }
        if (candidate.lightweight() && adapter.catalog().hasActiveFamily(parent, candidate.commonId())) {
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
        if (!adapter.capabilities().derivable()) {
            return skipped(parent, child, candidate, FailureReason.CAPABILITY_NOT_SUPPORTED);
        }
        LocalId id;
        do {
            id = LocalId.of(Long.toString(ids.nextId(null).longValue()));
        } while (id.value().equals(candidate.commonId().value()) || id.equals(candidate.sourceId()));
        LightTrackerItem item = LightTrackerItem.derive(id, candidate.commonId(), child.trackerId(), child.workingSetId(),
                candidate.revision(), candidate.sourceId(), candidate.sourceRevision(), classificationOf(child));
        if (!items.claimFamily(item)) {
            return new Outcome(IndexResult.Existing, null);
        }
        return new Outcome(IndexResult.Indexed, null);
    }

    private boolean isDeleted(BusinessObjectTypeAdapter adapter, LocalId sourceId) {
        try {
            return adapter.query(sourceId).evidence().deleted();
        } catch (DomainException failure) {
            if (FailureReason.of(failure) == FailureReason.NOT_FOUND) {
                return false;
            }
            throw failure;
        } catch (UnsupportedOperationException failure) {
            throw DomainException.withMessage(unsupportedEvidence(),
                    "Lifecycle evidence is unavailable for source " + sourceId.value());
        }
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

        static Candidate from(WorkingSetCreationSnapshotStore.Item item) {
            return new Candidate(item.localId(), item.commonId(), item.revision(),
                    item.sourceLocalId(), item.sourceRevision(), false);
        }
    }
}
