package alm.requirement.infrastructure;

import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;

public final class RequirementMaterializationAdapter implements RequirementWritePort {
    private final RequirementRepository repository;
    private final SnapshotStore snapshotStore;
    private final TransactionTemplate newTransactionTemplate;

    public RequirementMaterializationAdapter(RequirementRepository repository, SnapshotStore snapshotStore,
                                             TransactionTemplate newTransactionTemplate) {
        this.repository = repository;
        this.snapshotStore = snapshotStore;
        this.newTransactionTemplate = newTransactionTemplate;
    }

    @Override
    public void materialize(MaterializeRequest request) {
        var snapshot = snapshotStore.find(request.snapshotRef())
                .orElseThrow(() -> new MaterializationRejectedException("Source snapshot is missing"));
        if (!snapshot.localId().equals(request.sourceLocalId())
                || !snapshot.revision().equals(request.beforeRevision())
                || !snapshot.lockVersion().equals(request.beforeLockVersion())
                || !snapshot.commonId().equals(request.commonId())) {
            throw new MaterializationRejectedException("Source snapshot does not match event evidence");
        }

        newTransactionTemplate.executeWithoutResult(status -> {
            var existing = repository.findById(request.targetLocalId());
            if (existing.isPresent()) {
                verifyExisting(existing.get(), request, snapshot);
                return;
            }
            repository.save(Requirement.create(request.targetLocalId(), request.commonId(),
                    request.targetTrackerId(), snapshot.title(), snapshot.description(),
                    request.targetRevision(), snapshot.version()));
        });
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
