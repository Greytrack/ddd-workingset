package alm.requirement.application.query;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import alm.requirement.application.query.model.RequirementDetail;
import alm.requirement.application.query.provider.RequirementQueryProvider;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.application.common.InheritedEditTarget;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.LocalId;

/** Requirement 查询侧应用服务：读模型优先，继承目标回退。 */
public class RequirementQueryService {
    private final RequirementQueryProvider query;
    private final RequirementRepository repository;
    private final TrackerItemLifecyclePort lifecycle;
    private final WorkingSetRepository workingSets;

    public RequirementQueryService(RequirementQueryProvider query, RequirementRepository repository,
                                   TrackerItemLifecyclePort lifecycle, WorkingSetRepository workingSets) {
        this.query = Objects.requireNonNull(query, "query");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.workingSets = Objects.requireNonNull(workingSets, "workingSets");
    }

    @Transactional(readOnly = true)
    public RequirementDetail find(LocalId localId) {
        java.util.Optional<RequirementDetail> persisted = query.find(localId.numericValue());
        if (persisted.isPresent()) {
            if (persisted.get().deleted()) {
                throw DomainException.withMessage(FailureReason.DELETED, localId.value());
            }
            return persisted.get();
        }
        return lifecycle.findInheritedTarget(localId)
                .map(target -> {
                    workingSets.requireReady(target.workingSetId());
                    return detail(rebuildInherited(target));
                })
                .orElseGet(() -> detail(load(localId)));
    }

    @Transactional(readOnly = true)
    public java.util.List<RequirementDetail> findByTracker(sk.workingset.domain.WorkingSetId workingSetId,
                                                           sk.workingset.domain.TrackerId trackerId) {
        return query.findByTracker(trackerId.numericValue(), workingSetId.numericValue());
    }

    private Requirement rebuildInherited(InheritedEditTarget target) {
        Requirement source = load(target.sourceLocalId());
        requireActive(source);
        if (!source.revision().equals(target.sourceRevision())) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, target.sourceLocalId().value());
        }
        sk.workingset.application.common.TargetIdentity identity = target.identity();
        return Requirement.inherited(identity.localId(), identity.commonId(), identity.trackerId(),
                source.title(), source.description(), identity.revision(), source.version());
    }

    private Requirement load(LocalId localId) {
        return repository.findById(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "requirement not found: " + localId.value()));
    }

    private static void requireActive(Requirement requirement) {
        if (requirement.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, requirement.localId().value());
        }
    }

    private static RequirementDetail detail(Requirement requirement) {
        return new RequirementDetail(
                requirement.localId().numericValue(), requirement.commonId().numericValue(),
                requirement.trackerId().numericValue(), requirement.number(), requirement.title(),
                requirement.description(), requirement.revision().value(), requirement.version().value(),
                requirement.lockVersion().value(), requirement.deleted(), requirement.hasMaterialized());
    }
}
