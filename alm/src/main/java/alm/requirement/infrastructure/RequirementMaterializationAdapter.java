package alm.requirement.infrastructure;

import java.util.List;
import java.util.Objects;

import alm.requirement.application.RequirementMergeWriter;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.RequirementMergeCommand;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TargetIdentity;
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
    private final SnapshotStore snapshotStore;
    private final RequirementMergeWriter mergeWriter;

    public RequirementMaterializationAdapter(RequirementRepository repository, SnapshotStore snapshotStore,
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
        var snapshot = snapshotStore.find(request.snapshotRef())
                .orElseThrow(() -> new MaterializationRejectedException("Source snapshot is missing"));
        if (!snapshot.localId().equals(request.sourceLocalId())
                || !snapshot.revision().equals(request.beforeRevision())
                || !snapshot.lockVersion().equals(request.beforeLockVersion())
                || !snapshot.commonId().equals(request.commonId())) {
            throw new MaterializationRejectedException("Source snapshot does not match event evidence");
        }
        if (!target.localId().equals(request.targetLocalId())
                || !target.trackerId().equals(request.targetTrackerId())
                || !target.commonId().equals(request.commonId())) {
            throw new MaterializationRejectedException("Target identity does not match materialization evidence");
        }

        if (target.materialized()) {
            verifyMaterializedTarget(target);
            return;
        }
        var existing = repository.findById(request.targetLocalId());
        if (existing.isPresent()) {
            verifyExisting(existing.get(), request, snapshot);
        } else {
            repository.save(Requirement.create(request.targetLocalId(), request.commonId(),
                    request.targetTrackerId(), snapshot.title(), snapshot.description(),
                    request.targetRevision(), snapshot.version()));
        }
    }

    @Override
    public void verifyMaterializedTarget(TargetIdentity target) {
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
