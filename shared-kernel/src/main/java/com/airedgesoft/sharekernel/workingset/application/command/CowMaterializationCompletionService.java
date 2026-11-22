package com.airedgesoft.sharekernel.workingset.application.command;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.SharedKernelResultCode;
import com.airedgesoft.sharekernel.workingset.application.query.provider.CowFailureLogQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.cow.CowMaterializationStore;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemMaterializationCompletedEvent;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * 消费 ALM 的实例化结果：成功则切换 LTI，失败则落失败终态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class CowMaterializationCompletionService {
    private final CowMaterializationStore store;
    private final LightTrackerItemRepository items;
    private final CowFailureLogQueryProvider failureLog;
    private final TransactionTemplate transaction;


    /**
     * 消费物化结果：成功切换 LTI 为 Local-backed，失败记录失败终态。
     */
    public void complete(TrackerItemMaterializationCompletedEvent event) {
        if (event.outcome() == TrackerItemMaterializationCompletedEvent.Outcome.MATERIALIZED) {
            transaction.executeWithoutResult(status -> switchToLocalBacked(event));
        } else {
            transaction.executeWithoutResult(status -> fail(event));
        }
    }

    private void switchToLocalBacked(TrackerItemMaterializationCompletedEvent event) {
        CowMaterializationStore.Entry receipt = store.lockByEventAndTarget(event.eventId(), event.targetLocalId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND, "COW receipt is missing"));
        if (receipt.status() == CowMaterializationStore.Status.SWITCHED) {
            return;
        }
        store.markMaterialized(event.eventId(), event.targetLocalId());
        LightTrackerItem target = items.findByIdForUpdate(event.targetLocalId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND, "LightTrackerItem is missing"));
        if (!target.materialized() && (!target.sourceLocalId().equals(receipt.sourceLocalId())
                || !target.sourceRevision().equals(receipt.sourceRevision()))) {
            throw DomainException.withMessage(SharedKernelResultCode.EVENT_STATE_INVALID, "LightTrackerItem no longer matches the receipt");
        }
        if (!target.materialized()) {
            target.switchToLocalBacked(target.revision());
            items.save(target);
        }
        store.markSwitched(event.eventId(), event.targetLocalId());
    }

    private void fail(TrackerItemMaterializationCompletedEvent event) {
        CowMaterializationStore.Entry receipt = store.findByEventAndTarget(event.eventId(), event.targetLocalId())
                .orElse(null);
        store.markFailed(event.eventId(), event.targetLocalId(), event.failure());
        if (receipt != null) {
            failureLog.record(new CowFailureLogQueryProvider.Failure(event.eventId(), event.targetLocalId(),
                    receipt.sourceLocalId(), receipt.sourceRevision(), receipt.sourceRevision(),
                    event.beforeLockVersion(), event.afterLockVersion(), receipt.attempts(), "MATERIALIZE", "MATERIALIZATION_FAILED",
                    event.failure(), System.currentTimeMillis()));
        }
    }
}
