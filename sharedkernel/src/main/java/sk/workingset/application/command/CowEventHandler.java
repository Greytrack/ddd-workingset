package sk.workingset.application.command;

import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.CowFailureLog;
import sk.workingset.domain.repository.CowMaterializationRepository;

import java.time.Instant;
import java.util.List;

import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.application.command.dto.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.LightTrackerItem;

/**
 * COW 物化处理器：来源业务对象更新后，把未物化变体物化为本地内容。
 *
 * <p>D3-B：读目标身份、业务保存、SK 凭据/切换各用独立事务；失败记录后抛出聚合异常，不执行恢复。
 */
public final class CowEventHandler {
    private final LightTrackerItemRepository items;
    private final TrackerItemLifecyclePort lifecycle;
    private final RequirementWritePort writePort;
    private final CowFailureLog failureLog;
    private final TransactionTemplate transactionTemplate;
    private final CowMaterializationRepository store;

    public CowEventHandler(LightTrackerItemRepository items, TrackerItemLifecyclePort lifecycle,
                           RequirementWritePort writePort,
                           CowFailureLog failureLog,
                           TransactionTemplate transactionTemplate,
                           CowMaterializationRepository store) {
        this.items = items;
        this.lifecycle = lifecycle;
        this.writePort = writePort;
        this.failureLog = failureLog;
        this.transactionTemplate = transactionTemplate;
        this.store = store;
    }

    public void onTrackerItemChanged(BusinessObjectUpdateEvent event) {
        for (LightTrackerItem target : items.findBySource(event.sourceLocalId(), event.beforeRevision())) {
            if (target.materialized()) {
                continue;
            }
            String stage = "MATERIALIZE";
            int attempts = 1;
            try {
                transactionTemplate.executeWithoutResult(status -> store.markPending(event.eventId(), target.localId(),
                        event.sourceLocalId(), event.beforeRevision()));
                CowMaterializationRepository.Entry receipt = store.findByEventAndTarget(event.eventId(), target.localId()).orElseThrow();
                attempts = receipt.attempts();
                if (receipt.status() == CowMaterializationRepository.Status.SWITCHED) {
                    continue;
                }
                if (!receipt.businessSaved()) {
                    // D3-B：业务保存独立提交；SK 凭据与 LTI 切换随后各自独立事务。
                    transactionTemplate.executeWithoutResult(status -> writePort.materialize(
                            new MaterializeRequest(target.localId(), target.trackerId(),
                                    target.commonId(), event.sourceLocalId(), event.beforeRevision(),
                                    event.afterRevision(), event.beforeLockVersion(), event.afterLockVersion(),
                                    target.revision(), event.eventId(), event.snapshotRef()),
                            lifecycle.lockMaterializationTarget(target.trackerId(), target.localId())));
                    transactionTemplate.executeWithoutResult(status ->
                            store.markMaterialized(event.eventId(), target.localId()));
                }
                stage = "SWITCH";
                switchToLocalBacked(event.eventId(), target.localId());
            } catch (RuntimeException error) {
                CowFailureLog.Failure failure = new CowFailureLog.Failure(event.eventId(), target.localId(), event.sourceLocalId(),
                        event.beforeRevision(), event.afterRevision(), event.beforeLockVersion(),
                        event.afterLockVersion(), attempts, stage, error.getClass().getName(),
                        error.getMessage(), Instant.now());
                try {
                    transactionTemplate.executeWithoutResult(status ->
                            store.markFailed(event.eventId(), target.localId(), error.getMessage()));
                } catch (RuntimeException ignored) {
                    // 失败终态以原异常为准；失败记录本身不再触发恢复。
                }
                try {
                    failureLog.record(failure);
                } catch (RuntimeException ignored) {
                    // 失败终态以原异常为准；失败记录本身不再触发恢复。
                }
            }
        }
    }
    private void switchToLocalBacked(long eventId, sk.workingset.domain.LocalId targetId) {
        transactionTemplate.executeWithoutResult(status -> {
            CowMaterializationRepository.Entry receipt = store.lockByEventAndTarget(eventId, targetId)
                    .orElseThrow(() -> sk.common.error.DomainException.withMessage(
                            sk.workingset.error.WorkingsetResultCode.MATERIALIZATION_REJECTED,
                            "COW receipt is missing"));
            if (receipt.status() == CowMaterializationRepository.Status.SWITCHED) {
                return;
            }
            if (!receipt.businessSaved()) {
                throw sk.common.error.DomainException.withMessage(
                        sk.workingset.error.WorkingsetResultCode.MATERIALIZATION_REJECTED,
                        "Target business save was not confirmed");
            }
            LightTrackerItem target = items.findById(targetId)
                    .orElseThrow(() -> sk.common.error.DomainException.withMessage(
                            sk.workingset.error.WorkingsetResultCode.MATERIALIZATION_REJECTED,
                            "LightTrackerItem is missing"));
            if (!target.materialized() && (!target.sourceLocalId().equals(receipt.sourceLocalId())
                    || !target.sourceRevision().equals(receipt.sourceRevision()))) {
                throw sk.common.error.DomainException.withMessage(
                        sk.workingset.error.WorkingsetResultCode.MATERIALIZATION_REJECTED,
                        "LightTrackerItem no longer matches the receipt");
            }
            if (!target.materialized()) {
                target.switchToLocalBacked(target.revision());
                items.save(target);
            }
            store.markSwitched(eventId, targetId);
        });
    }
}