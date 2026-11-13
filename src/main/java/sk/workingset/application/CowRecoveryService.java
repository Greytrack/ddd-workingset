package sk.workingset.application;

import java.util.List;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TargetIdentity;
import sk.workingset.application.port.TargetTransactionRunner;
import sk.workingset.domain.LocalId;

/**
 * COW 恢复服务：重投未完成事件，或对“业务已保存但未切换”的物化收据做切换补偿。
 *
 * <p>切换动作幂等；业务保存凭据缺失时拒绝继续，避免伪造完成。
 */
public final class CowRecoveryService {
    private final CowMaterializationStore store;
    private final LightTrackerItemRepository items;
    private final RequirementWritePort writePort;
    private final CowFailureLog failures;
    private final TargetTransactionRunner transactions;
    private final Runnable resubmit;

    public CowRecoveryService(CowMaterializationStore store, LightTrackerItemRepository items,
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
                    .orElseThrow(() -> new MaterializationRejectedException("COW receipt is missing"));
            if (receipt.status() == CowMaterializationStore.Status.SWITCHED) {
                return;
            }
            if (!receipt.businessSaved()) {
                throw new MaterializationRejectedException("Target business save was not confirmed");
            }
            var target = items.findById(targetId)
                    .orElseThrow(() -> new MaterializationRejectedException("LightTrackerItem is missing"));
            if (!target.materialized() && (!target.sourceLocalId().equals(receipt.sourceLocalId())
                    || !target.sourceRevision().equals(receipt.sourceRevision()))) {
                throw new MaterializationRejectedException("LightTrackerItem no longer matches the receipt");
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

    public RecoveryDetails listFailures(LocalId source) {
        return new RecoveryDetails(failures.findBySource(source), store.findBySource(source));
    }

    public record RecoveryDetails(List<CowFailureLog.Failure> failures,
                                  List<CowMaterializationStore.Entry> materializations) {}
}
