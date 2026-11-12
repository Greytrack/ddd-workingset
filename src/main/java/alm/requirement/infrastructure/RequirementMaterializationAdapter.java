package alm.requirement.infrastructure;

import java.util.List;
import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import alm.requirement.application.RequirementMergeWriter;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.RequirementMergeCommand;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

public final class RequirementMaterializationAdapter implements RequirementWritePort {
    private final RequirementRepository repository;
    private final SnapshotStore snapshotStore;
    private final TransactionTemplate newTransactionTemplate;
    private final RequirementMergeWriter mergeWriter;
    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;

    public RequirementMaterializationAdapter(RequirementRepository repository, SnapshotStore snapshotStore,
                                             TransactionTemplate newTransactionTemplate) {
        this(repository, snapshotStore, newTransactionTemplate, null, null, null);
    }

    public RequirementMaterializationAdapter(RequirementRepository repository, SnapshotStore snapshotStore,
                                             TransactionTemplate newTransactionTemplate, RequirementMergeWriter mergeWriter,
                                             TrackerRepository trackers, LightTrackerItemRepository items) {
        this.repository = repository;
        this.snapshotStore = snapshotStore;
        this.newTransactionTemplate = newTransactionTemplate;
        this.mergeWriter = mergeWriter;
        this.trackers = trackers;
        this.items = items;
    }

    @Override
    public void lockMergeEvidence(List<LocalId> ids) {
        mergeWriter.lockEvidence(ids);
    }

    @Override
    public Revision applyMerge(RequirementMergeCommand command) {
        return mergeWriter.apply(command);
    }

    @Override
    public void materialize(MaterializeRequest request, Runnable recordBusinessSave) {
        var snapshot = snapshotStore.find(request.snapshotRef())
                .orElseThrow(() -> new MaterializationRejectedException("Source snapshot is missing"));
        if (!snapshot.localId().equals(request.sourceLocalId())
                || !snapshot.revision().equals(request.beforeRevision())
                || !snapshot.lockVersion().equals(request.beforeLockVersion())
                || !snapshot.commonId().equals(request.commonId())) {
            throw new MaterializationRejectedException("Source snapshot does not match event evidence");
        }

        newTransactionTemplate.executeWithoutResult(status -> {
            if (items != null) {
                trackers.lockTracker(request.targetTrackerId());
                var target = items.findById(request.targetLocalId()).orElseThrow(
                        () -> new MaterializationRejectedException("Target variant is missing"));
                if (target.materialized()) {
                    verifyMaterializedTarget(target);
                    recordBusinessSave.run();
                    return;
                }
            }
            var existing = repository.findById(request.targetLocalId());
            if (existing.isPresent()) {
                verifyExisting(existing.get(), request, snapshot);
            } else {
                repository.save(Requirement.create(request.targetLocalId(), request.commonId(),
                        request.targetTrackerId(), snapshot.title(), snapshot.description(),
                        request.targetRevision(), snapshot.version()));
            }
            recordBusinessSave.run();
        });
    }

    @Override
    public void verifyMaterializedTarget(LightTrackerItem target) {
        var existing = repository.findById(target.localId())
                .orElseThrow(() -> new MaterializationRejectedException("Target business object is missing"));
        if (!existing.commonId().equals(target.commonId())
                || !existing.trackerId().equals(target.trackerId())
                || !existing.revision().equals(target.revision())) {
            throw new MaterializationRejectedException("Target business identity does not match the receipt");
        }
    }

    private static void verifyExisting(Requirement existing, MaterializeRequest request,
                                       RequirementSnapshot snapshot) {
        if (!existing.commonId().equals(request.commonId())
                || !existing.trackerId().equals(request.targetTrackerId())
                || !existing.revision().equals(request.targetRevision())
                || !Objects.equals(existing.title(), snapshot.title())
                || !Objects.equals(existing.description(), snapshot.description())
                || !Objects.equals(existing.version(), snapshot.version())) {
            throw new MaterializationRejectedException("Existing target does not match materialization evidence");
        }
    }
}
