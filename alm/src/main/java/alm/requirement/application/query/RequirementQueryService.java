package alm.requirement.application.query;

import alm.requirement.application.query.model.RequirementDetail;
import alm.requirement.application.query.provider.RequirementQueryProvider;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;

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

    private static DomainException notFound(LocalId localId) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, "requirement not found: " + localId.value());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RequirementDetail find(LocalId localId) {
        Optional<RequirementDetail> persisted = query.find(localId.numericValue());
        if (persisted.isEmpty()) {
            return findInherited(localId);
        }
        RequirementDetail detail = persisted.get();
        if (detail.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, localId.value());
        }
        return detail;
    }

    @Transactional(readOnly = true)
    public List<RequirementDetail> findByTracker(WorkingSetId workingSetId, TrackerId trackerId) {
        return query.findByTracker(trackerId.numericValue(), workingSetId.numericValue());
    }

    private RequirementDetail findInherited(LocalId localId) {
        LightTrackerItem target = items.findById(localId)
                .filter(item -> !item.materialized())
                .orElseThrow(() -> notFound(localId));
        WorkingSetId workingSetId = target.workingSetId();
        workingSets.findById(workingSetId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, workingSetId.value()));
        Requirement source = repository.findById(target.sourceLocalId())
                .orElseThrow(() -> notFound(target.sourceLocalId()));
        if (source.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, source.localId().value());
        }
        if (!source.revision().equals(target.sourceRevision())) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, target.sourceLocalId().value());
        }
        Requirement requirement = Requirement.inherited(target.localId(), target.commonId(), target.trackerId(),
                source.title(), source.description(), target.revision(), source.version());
        return new RequirementDetail(
                requirement.localId().numericValue(), requirement.commonId().numericValue(),
                requirement.trackerId().numericValue(), requirement.number(), requirement.title(),
                requirement.description(), requirement.revision().value(), requirement.version().value(),
                requirement.lockVersion().value(), requirement.deleted(), requirement.hasMaterialized());
    }
}
