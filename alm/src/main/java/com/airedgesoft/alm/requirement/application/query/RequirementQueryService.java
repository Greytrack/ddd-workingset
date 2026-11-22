package com.airedgesoft.alm.requirement.application.query;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.application.query.provider.RequirementQueryProvider;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Requirement 查询侧应用服务：读模型优先，继承目标回退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementQueryService {
    private final RequirementQueryProvider query;
    private final RequirementRepository repository;
    private final LightTrackerItemRepository items;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;

    private static DomainException notFound(Long localId) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, "requirement not found: " + Long.toString(localId));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RequirementDetail find(Long localId) {
        Optional<RequirementDetail> persisted = query.find(localId);
        if (persisted.isEmpty()) {
            return findInherited(localId);
        }
        RequirementDetail detail = persisted.get();
        if (detail.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, Long.toString(localId));
        }
        return detail;
    }

    /**
     * 共享 Tracker 的 Item 属于其权威工作集，但通过绑定对所有子工作集可见；
     * 非共享 Tracker 的 Item 属于单一工作集。工作集参数用于可见性校验，列表按 Tracker 读取。
     */
    @Transactional(readOnly = true)
    public List<RequirementDetail> findByTracker(Long workingSetId, Long trackerId) {
        Tracker tracker = trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, Long.toString(trackerId)));
        if (!visibleIn(tracker, workingSetId)) {
            return List.of();
        }
        return query.findByTracker(trackerId);
    }

    private boolean visibleIn(Tracker tracker, Long workingSetId) {
        if (tracker.workingSetId().equals(workingSetId)) {
            return true;
        }
        if (tracker.sharingMode() != SharingMode.SHARED) {
            return false;
        }
        return workingSets.findById(workingSetId)
                .map(workingSet -> workingSet.hasTrackerBinding(tracker.trackerId()))
                .orElse(false);
    }

    private RequirementDetail findInherited(Long localId) {
        LightTrackerItem target = items.findById(localId)
                .filter(item -> !item.materialized())
                .orElseThrow(() -> notFound(localId));
        Long workingSetId = target.workingSetId();
        workingSets.findById(workingSetId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(workingSetId)));
        Requirement source = repository.findById(target.sourceLocalId())
                .orElseThrow(() -> notFound(target.sourceLocalId()));
        if (source.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, Long.toString(source.localId()));
        }
        if (!source.revision().equals(target.sourceRevision())) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, Long.toString(target.sourceLocalId()));
        }
        Requirement requirement = Requirement.inherited(target.localId(), target.commonId(), target.trackerId(),
                source.title(), source.description(), target.revision(), source.version());
        return new RequirementDetail(
                requirement.localId(), requirement.commonId(),
                requirement.trackerId(), requirement.number(), requirement.title(),
                requirement.description(), requirement.revision(), requirement.version(),
                requirement.lockVersion(), requirement.deleted(), requirement.hasMaterialized());
    }
}
