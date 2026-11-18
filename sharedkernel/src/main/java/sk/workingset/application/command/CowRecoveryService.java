package sk.workingset.application.command;

import sk.workingset.application.CowFailureLog;
import sk.workingset.domain.repository.CowMaterializationRepository;

import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.application.port.TargetTransactionRunner;
import sk.workingset.domain.LocalId;

/**
 * COW 恢复服务：重投未完成事件，或对“业务已保存但未切换”的物化收据做切换补偿。
 *
 * <p>切换动作幂等；业务保存凭据缺失时拒绝继续，避免伪造完成。
 */
public final class CowRecoveryService {
    private final CowMaterializationRepository store;
    private final LightTrackerItemRepository items;
    private final RequirementWritePort writePort;
    private final CowFailureLog failures;
    private final TargetTransactionRunner transactions;
    private final Runnable resubmit;

    public CowRecoveryService(CowMaterializationRepository store, LightTrackerItemRepository items,
                              RequirementWritePort writePort, CowFailureLog failures,
                              TargetTransactionRunner transactions, Runnable resubmit) {
        this.store = store;
        this.items = items;
        this.writePort = writePort;
        this.failures = failures;
        this.transactions = transactions;
        this.resubmit = resubmit;
    }

    public void recoverIncompletePublications() {
        resubmit.run();
    }

    public void recoverSwitchOnly(long eventId, LocalId targetId) {
        transactions.inNewTransaction(() -> {
            var receipt = store.lockByEventAndTarget(eventId, targetId)
                    .orElseThrow(() -> DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "COW receipt is missing"));
            if (receipt.status() == CowMaterializationRepository.Status.SWITCHED) {
                return;
            }
            if (!receipt.businessSaved()) {
                throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target business save was not confirmed");
            }
            var target = items.findById(targetId)
                    .orElseThrow(() -> DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "LightTrackerItem is missing"));
            if (!target.materialized() && (!target.sourceLocalId().equals(receipt.sourceLocalId())
                    || !target.sourceRevision().equals(receipt.sourceRevision()))) {
                throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "LightTrackerItem no longer matches the receipt");
            }
            writePort.verifyMaterializedTarget(TargetIdentity.of(target));
            if (!target.materialized()) {
                target.switchToLocalBacked(target.revision());
                items.save(target);
            }
            store.markSwitched(eventId, targetId);
            failures.resolve(eventId, targetId);
        });
    }

}
