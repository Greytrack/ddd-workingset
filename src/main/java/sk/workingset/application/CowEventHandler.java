package sk.workingset.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TargetTransactionRunner;
import sk.workingset.domain.LightTrackerItem;

public final class CowEventHandler {
    private final LightTrackerItemRepository items;
    private final RequirementWritePort writePort;
    private final CowFailureLog failureLog;
    private final TargetTransactionRunner transactionRunner;

    public CowEventHandler(LightTrackerItemRepository items, RequirementWritePort writePort,
                           CowFailureLog failureLog, TargetTransactionRunner transactionRunner) {
        this.items = items;
        this.writePort = writePort;
        this.failureLog = failureLog;
        this.transactionRunner = transactionRunner;
    }

    public void onRequirementChanged(RequirementChanged event) {
        List<CowFailureLog.Failure> failures = new ArrayList<>();
        List<RuntimeException> causes = new ArrayList<>();
        for (LightTrackerItem target : items.findBySource(event.sourceLocalId(), event.beforeRevision())) {
            if (target.materialized()) {
                continue;
            }
            String stage = "MATERIALIZE";
            try {
                writePort.materialize(new MaterializeRequest(target.localId(), target.trackerId(),
                        target.commonId(), event.sourceLocalId(), event.beforeRevision(), event.afterRevision(),
                        event.beforeLockVersion(), event.afterLockVersion(), target.revision(),
                        event.eventId(), event.snapshotRef()));
                stage = "SWITCH";
                transactionRunner.inNewTransaction(() -> {
                    target.switchToLocalBacked(target.revision());
                    items.save(target);
                });
            } catch (RuntimeException error) {
                var failure = new CowFailureLog.Failure(event.eventId(), target.localId(), event.sourceLocalId(),
                        event.beforeRevision(), event.afterRevision(), stage, error.getClass().getName(),
                        error.getMessage(), Instant.now());
                failures.add(failure);
                causes.add(error);
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
