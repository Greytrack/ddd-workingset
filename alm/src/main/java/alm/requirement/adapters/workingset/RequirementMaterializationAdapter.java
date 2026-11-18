package alm.requirement.adapters.workingset;

import java.util.Optional;

import alm.requirement.application.command.RequirementSnapshot;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.domain.SnapshotRepository;
import sk.common.error.DomainException;
import sk.workingset.application.command.dto.MaterializeRequest;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.error.WorkingsetResultCode;

/**
 * ALM 写入协作 Adapter（D4 方案 A）：只负责业务对象的保存与身份校验。
 */
public final class RequirementMaterializationAdapter implements RequirementWritePort {
    private final RequirementRepository repository;
    private final SnapshotRepository snapshotStore;

    public RequirementMaterializationAdapter(RequirementRepository repository, SnapshotRepository snapshotStore) {
        this.repository = repository;
        this.snapshotStore = snapshotStore;
    }

    @Override
    public void materialize(MaterializeRequest request, TargetIdentity target) {
        RequirementSnapshot snapshot = snapshotStore.find(request.snapshotRef())
                .orElseThrow(() -> DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Source snapshot is missing"));
        if (!snapshot.localId().equals(request.sourceLocalId())
                || !snapshot.revision().equals(request.beforeRevision())
                || !snapshot.lockVersion().equals(request.beforeLockVersion())
                || !snapshot.commonId().equals(request.commonId())) {
            throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Source snapshot does not match event evidence");
        }
        if (!target.localId().equals(request.targetLocalId())
                || !target.trackerId().equals(request.targetTrackerId())
                || !target.commonId().equals(request.commonId())) {
            throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target identity does not match materialization evidence");
        }

        if (target.materialized()) {
            return;
        }
        Optional<Requirement> existing = repository.findById(request.targetLocalId());
        if (existing.isPresent()) {
            Requirement stored = existing.orElseThrow();
            if (!stored.hasMaterialized()) {
                throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED,
                        "Target business object is not materialized");
            }
        } else {
            repository.save(Requirement.create(request.targetLocalId(), request.commonId(),
                    request.targetTrackerId(), snapshot.title(), snapshot.description(),
                    request.targetRevision(), snapshot.version()));
        }
    }

}
