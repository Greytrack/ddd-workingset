package sk.workingset.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TargetTransactionRunner;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.LightTrackerItem;

public final class CowEventHandler {
    private final LightTrackerItemRepository items;
    private final TrackerItemLifecyclePort lifecycle;
    private final RequirementWritePort writePort;
    private final CowFailureLog failureLog;
    private final TargetTransactionRunner transactionRunner;
    private final CowMaterializationStore store;
    private final CowRecoveryService recovery;

    public CowEventHandler(LightTrackerItemRepository items, TrackerItemLifecyclePort lifecycle,
                           RequirementWritePort writePort,
                           CowFailureLog failureLog, TargetTransactionRunner transactionRunner,
                           CowMaterializationStore store, CowRecoveryService recovery) {
        this.items = items;
        this.lifecycle = lifecycle;
        this.writePort = writePort;
        this.failureLog = failureLog;
        this.transactionRunner = transactionRunner;
        this.store = store;
        this.recovery = recovery;
    }

    public void onRequirementChanged(RequirementChanged event) {
        List<CowFailureLog.Failure> failures = new ArrayList<>();
        List<RuntimeException> causes = new ArrayList<>();
        for (LightTrackerItem target : items.findBySource(event.sourceLocalId(), event.beforeRevision())) {
            if (target.materialized()) {
                continue;
            }
            String stage = "MATERIALIZE";
            int attempts = 1;
            try {
                transactionRunner.inNewTransaction(() -> store.markPending(event.eventId(), target.localId(),
                        event.sourceLocalId(), event.beforeRevision()));
                var receipt = store.findByEventAndTarget(event.eventId(), target.localId()).orElseThrow();
                attempts = receipt.attempts();
                if (receipt.status() == CowMaterializationStore.Status.SWITCHED) {
                    continue;
                }
                if (!receipt.businessSaved()) {
                    transactionRunner.inNewTransaction(() -> {
                        var targetIdentity = lifecycle.lockMaterializationTarget(
                                target.trackerId(), target.localId());
                        writePort.materialize(new MaterializeRequest(target.localId(), target.trackerId(),
                                target.commonId(), event.sourceLocalId(), event.beforeRevision(), event.afterRevision(),
                                event.beforeLockVersion(), event.afterLockVersion(), target.revision(),
                                event.eventId(), event.snapshotRef()), targetIdentity,
                                () -> store.markMaterialized(event.eventId(), target.localId()));
                    });
                }
                stage = "SWITCH";
                recovery.recoverSwitchOnly(event.eventId(), target.localId());
            } catch (RuntimeException error) {
                var failure = new CowFailureLog.Failure(event.eventId(), target.localId(), event.sourceLocalId(),
                        event.beforeRevision(), event.afterRevision(), event.beforeLockVersion(),
                        event.afterLockVersion(), attempts, null, stage, error.getClass().getName(),
                        error.getMessage(), Instant.now());
                failures.add(failure);
                causes.add(error);
                try {
                    transactionRunner.inNewTransaction(() ->
                            store.markFailed(event.eventId(), target.localId(), error.getMessage()));
                } catch (RuntimeException recordingError) {
                    causes.add(recordingError);
                }
                try {
                    failureLog.record(failure);
                } catch (RuntimeException recordingError) {
                    causes.add(recordingError);
                }
            }
        }
        if (!failures.isEmpty()) {
            var error = new CowMaterializationFailedException(event.eventId(), failures);
            causes.forEach(error::addSuppressed);
            throw error;
        }
    }
}
