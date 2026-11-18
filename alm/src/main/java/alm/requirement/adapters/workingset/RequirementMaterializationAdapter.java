package alm.requirement.adapters.workingset;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import alm.requirement.application.command.RequirementSnapshot;
import alm.requirement.domain.SnapshotRepository;
import alm.requirement.application.command.RequirementMergeWriter;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;
import sk.workingset.application.command.dto.MaterializeRequest;
import sk.workingset.application.command.dto.RequirementMergeCommand;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.domain.LocalId;

/**
 * ALM 写入协作 Adapter（D4 方案 A）。
 *
 * <p>只负责业务对象：重建来源快照、校验身份证据、保存或校验 Requirement。
 * Tracker 锁定与 LightTrackerItem 读取由 SK 承担，本类不依赖任何 SK 仓储。
 * 事务边界由调用方（SK 的 COW 流程）提供。
 */
public final class RequirementMaterializationAdapter implements RequirementWritePort {
    private final RequirementRepository repository;
    private final SnapshotRepository snapshotStore;
    private final RequirementMergeWriter mergeWriter;

    public RequirementMaterializationAdapter(RequirementRepository repository, SnapshotRepository snapshotStore,
                                             RequirementMergeWriter mergeWriter) {
        this.repository = repository;
        this.snapshotStore = snapshotStore;
        this.mergeWriter = mergeWriter;
    }

    @Override
    public void lockMergeEvidence(List<LocalId> ids) {
        mergeWriter.lockEvidence(ids);
    }

    @Override
    public RequirementMergeCommand.Result applyMerge(RequirementMergeCommand command) {
        return mergeWriter.apply(command);
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
            verifyMaterializedTarget(target);
            return;
        }
        Optional<Requirement> existing = repository.findById(request.targetLocalId());
        if (existing.isPresent()) {
            Requirement stored = existing.orElseThrow();
            if (!stored.hasMaterialized()) {
                throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED,
                        "Target business object is not materialized");
            }
            verifyExisting(stored, request, snapshot);
        } else {
            repository.save(Requirement.create(request.targetLocalId(), request.commonId(),
                    request.targetTrackerId(), snapshot.title(), snapshot.description(),
                    request.targetRevision(), snapshot.version()));
        }
    }

    @Override
    public void verifyMaterializedTarget(TargetIdentity target) {
        Requirement existing = repository.findById(target.localId())
                .orElseThrow(() -> DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target business object is missing"));
        if (!existing.hasMaterialized()
                || !existing.commonId().equals(target.commonId())
                || !existing.trackerId().equals(target.trackerId())
                || !existing.revision().equals(target.revision())) {
            throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target business identity does not match the receipt");
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
            throw DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Existing target does not match materialization evidence");
        }
    }
}
