package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowFailureLogQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowRequestPublications;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationCompletedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
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
    private final CowRequestPublications requests;
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
        LightTrackerItem target = items.findByIdForUpdate(event.targetLocalId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND, "LightTrackerItem is missing"));
        if (target.materialized()) {
            return;
        }
        TrackerItemMaterializationRequestedEvent request = requestFor(event);
        if (!target.sourceLocalId().equals(request.sourceLocalId())
                || !target.sourceRevision().equals(request.beforeRevision())
                || !target.revision().equals(request.targetRevision())
                || !target.localId().equals(event.businessRef())
                || !target.revision().equals(event.revision())) {
            throw DomainException.withMessage(SharedKernelResultCode.EVENT_STATE_INVALID, "LightTrackerItem no longer matches the COW request");
        }
        target.switchToLocalBacked(target.revision());
        items.save(target);
    }

    private void fail(TrackerItemMaterializationCompletedEvent event) {
        if (items.findByIdForUpdate(event.targetLocalId()).isEmpty()) {
            return;
        }
        TrackerItemMaterializationRequestedEvent request = requestFor(event);
        boolean recorded = failureLog.findBySource(request.sourceLocalId()).stream()
                .anyMatch(failure -> failure.eventId() == event.eventId()
                        && failure.targetLocalId().equals(event.targetLocalId()));
        if (!recorded) {
            failureLog.record(new CowFailureLogQueryProvider.Failure(event.eventId(), event.targetLocalId(),
                    request.sourceLocalId(), request.beforeRevision(), request.afterRevision(),
                    request.beforeLockVersion(), request.afterLockVersion(), 1, "MATERIALIZE", "MATERIALIZATION_FAILED",
                    event.failure(), System.currentTimeMillis()));
        }
    }

    private TrackerItemMaterializationRequestedEvent requestFor(TrackerItemMaterializationCompletedEvent event) {
        return requests.find(event.eventId(), event.targetLocalId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND, "COW request is missing"));
    }
}
