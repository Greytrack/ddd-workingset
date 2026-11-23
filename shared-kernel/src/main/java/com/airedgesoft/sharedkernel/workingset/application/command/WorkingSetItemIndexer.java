package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.RequestThreadHolder;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.port.TrackerItemSourceLock;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemSummary;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.Failure;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.IndexProgress;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.ItemOutcome;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.Stage;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.SavepointManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Pages parent Items using the baseline time stored with the creation task.
 *
 * <p>候选只来自父 Tracker 的 LTI；来源校验使用通用业务对象登记中的状态和版次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class WorkingSetItemIndexer {


    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemQueryProvider itemQueries;
    private final LightTrackerItemRepository items;
        private final TrackerItemSourceLock sourceLock;

    @Value("${workingset.creation.index-batch-size:100}")
    private int batchSize;

    static final long FOLDER_CLASSIFICATION_ID = 10070101L;

    private static Failure failure(Long parent, Tracker child, Long localId, FailureReason reason) {
        return new Failure(Stage.INDEX_UNSHARED_TRACKERS, child.productSpaceId().toString(), Long.toString(parent),
                localId == null ? null : Long.toString(localId), reason.name(), System.currentTimeMillis());
    }

    private static FailureReason reason(RuntimeException failure) {
        if (failure instanceof PessimisticLockingFailureException concurrencyConflict) {
            throw concurrencyConflict;
        }
        if (failure instanceof DomainException domainFailure) {
            return FailureReason.of(domainFailure);
        }
        log.warn("Working set indexing operation failed", failure);
        return failure instanceof DataAccessResourceFailureException
                ? FailureReason.DEPENDENCY_UNAVAILABLE : FailureReason.UNKNOWN;
    }

    /**
     * 按当前 Tracker 的基线时间分页索引 Item，返回推进后的任务进度。
     */
    public WorkingSetCreation indexBatch(WorkingSetCreation creation, SavepointManager transaction) {
        Long parent = Long.valueOf(creation.currentItem());
        Long target = Long.valueOf(creation.request().newWorkingSetId());
        if (creation.failures().stream().anyMatch(failure -> Long.toString(parent).equals(failure.trackerId())
                && (failure.stage() == Stage.COPY_UNSHARED_TRACKERS || failure.stage() == Stage.BIND_PRODUCT_AND_TRACKER))) {
            return creation.advance(ItemOutcome.Skipped, null, System.currentTimeMillis());
        }
        Tracker child = trackers.findDerivedTracker(target, parent).orElse(null);
        WorkingSet targetSet = workingSets.findByIdForCreation(target).orElseThrow();
        if (child == null || !targetSet.hasTrackerBinding(child.trackerId())) {
            return creation.advance(ItemOutcome.Skipped, null, System.currentTimeMillis());
        }
        long baselineAt = creation.baselineAt(Long.toString(parent));
        WorkingSetCreation.IndexProgress progress = creation.indexProgress().getOrDefault(Long.toString(parent), IndexProgress.initial());
        Object savepoint = transaction.createSavepoint();
        List<LightTrackerItemSummary> page;
        try {
            page = itemQueries.pageInTracker(parent, baselineAt, progress.afterLocalId(), batchSize);
        } catch (RuntimeException failure) {
            transaction.rollbackToSavepoint(savepoint);
            FailureReason reason = reason(failure);
            // The default Folder Tracker can exist before a Folder business adapter is installed.
            if (reason == FailureReason.CAPABILITY_NOT_SUPPORTED
                    && child.numericClassificationId() == FOLDER_CLASSIFICATION_ID && !itemQueries.existsInTracker(parent)) {
                return creation.recordIndexBatch(new IndexProgress(progress.afterLocalId(), progress.succeeded(),
                        progress.failed(), progress.skipped(), Long.toString(child.trackerId())), List.of(), true, System.currentTimeMillis());
            }
            return creation.advance(ItemOutcome.Failed, failure(parent, child, null, reason), System.currentTimeMillis());
        } finally {
            transaction.releaseSavepoint(savepoint);
        }

        ArrayList<WorkingSetCreation.Failure> failures = new ArrayList<Failure>();
        int succeeded = progress.succeeded();
        int skipped = progress.skipped();
        int failed = progress.failed();
        for (LightTrackerItemSummary candidate : page) {
            Object itemSavepoint = transaction.createSavepoint();
            try {
                Outcome outcome = index(parent, child, candidate);
                switch (outcome.result()) {
                    case Indexed -> succeeded++;
                    case Existing -> skipped++;
                    case Skipped -> {
                        skipped++;
                        failures.add(outcome.failure());
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
        String cursor = page.isEmpty() ? progress.afterLocalId() : Long.toString(page.getLast().localId());
        return creation.recordIndexBatch(new IndexProgress(cursor, succeeded, failed, skipped,
                finished ? Long.toString(child.trackerId()) : null), failures, finished, System.currentTimeMillis());
    }

    private Outcome index(Long parent, Tracker child, LightTrackerItemSummary candidate) {
        // 重入/重复执行时先认已提交的同族，避免来源后来软删或不可读被误记为失败。
        if (itemQueries.hasFamily(child.workingSetId(), candidate.commonId())) {
            return new Outcome(IndexResult.Existing, null);
        }
        Long sourceId = candidate.effectiveContentSource();
        Integer sourceRevision = candidate.effectiveContentRevision();
        TrackerItemSourceLock.State source = sourceLock.read(sourceId).orElse(null);
        if (source == null) {
            return new Outcome(IndexResult.Skipped, failure(parent, child, candidate.localId(), FailureReason.NOT_FOUND));
        }
        if (source.deleted()) {
            return new Outcome(IndexResult.Skipped, failure(parent, child, candidate.localId(), FailureReason.DELETED));
        }
        if (!sourceRevision.equals(source.revision())) {
            return new Outcome(IndexResult.Skipped, failure(parent, child, candidate.localId(), FailureReason.HISTORY_UNAVAILABLE));
        }
        Long id;
        do {
            id = Ids.nextId();
        } while (Long.toString(id).equals(Long.toString(candidate.commonId())) || id.equals(sourceId));
        LightTrackerItem item = LightTrackerItem.derive(id, candidate.commonId(), child.trackerId(), child.workingSetId(),
                candidate.revision(), sourceId, sourceRevision, child.productSpaceId(), child.numericClassificationId(), child.classId(),
                RequestThreadHolder.getUserId());
        if (!items.claimFamily(item)) {
            return new Outcome(IndexResult.Existing, null);
        }
        return new Outcome(IndexResult.Indexed, null);
    }

    private enum IndexResult {Indexed, Existing, Skipped}

    private record Outcome(IndexResult result, Failure failure) {
    }

}
