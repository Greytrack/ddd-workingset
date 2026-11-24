package com.airedgesoft.alm.requirement.application.command;

import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;
import com.airedgesoft.alm.requirement.domain.RequirementSnapshotStore;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import com.airedgesoft.sharedkernel.workingset.application.command.dto.MaterializeRequest;
import com.airedgesoft.sharedkernel.workingset.application.common.TargetIdentity;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * ALM 物化服务：只负责业务对象保存与身份校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementMaterializationService {
    private final RequirementRepository repository;
    private final RequirementSnapshotStore snapshotStore;

    private static void verifyExisting(Requirement existing, MaterializeRequest request,
                                       RequirementSnapshot snapshot) {
        if (!existing.commonId().equals(request.commonId())
                || !existing.trackerId().equals(request.targetTrackerId())
                || !existing.revision().equals(request.targetRevision())
                || !Objects.equals(existing.title(), snapshot.title())
                || !Objects.equals(existing.description(), snapshot.description())
                || !Objects.equals(existing.version(), snapshot.version())) {
            throw new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED,
                    "Existing target does not match materialization evidence");
        }
    }

    /**
     * 按物化证据保存目标业务对象；已物化则校验与收据身份一致。
     */
    public void materialize(MaterializeRequest request, TargetIdentity target) {
        RequirementSnapshot snapshot = snapshotStore.find(request.snapshotRef())
                .orElseThrow(() -> new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Source snapshot is missing"));
        if (!snapshot.localId().equals(request.sourceLocalId())
                || !snapshot.revision().equals(request.beforeRevision())
                || !snapshot.lockVersion().equals(request.beforeLockVersion())
                || !snapshot.commonId().equals(request.commonId())) {
            throw new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Source snapshot does not match event evidence");
        }
        if (!target.localId().equals(request.targetLocalId())
                || !target.trackerId().equals(request.targetTrackerId())
                || !target.commonId().equals(request.commonId())) {
            throw new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target identity does not match materialization evidence");
        }

        if (target.materialized()) {
            verifyMaterializedTarget(target);
            return;
        }
        Optional<Requirement> existing = repository.findById(request.targetLocalId());
        if (existing.isPresent()) {
            Requirement stored = existing.orElseThrow();
            if (!stored.hasMaterialized()) {
                throw new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED,
                        "Target business object is not materialized");
            }
            verifyExisting(stored, request, snapshot);
        } else {
            repository.save(Requirement.create(request.targetLocalId(), request.commonId(),
                    request.targetTrackerId(), snapshot.title(), snapshot.description(),
                    request.targetRevision(), snapshot.version(), Long.toString(AuthorizationScope.currentUserId())));
        }
    }

    /**
     * 校验已物化目标与收据的家族、Tracker 与版次一致。
     */
    public void verifyMaterializedTarget(TargetIdentity target) {
        Requirement existing = repository.findById(target.localId())
                .orElseThrow(() -> new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target business object is missing"));
        if (!existing.hasMaterialized()
                || !existing.commonId().equals(target.commonId())
                || !existing.trackerId().equals(target.trackerId())
                || !existing.revision().equals(target.revision())) {
            throw new DomainException(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target business identity does not match the receipt");
        }
    }
}
