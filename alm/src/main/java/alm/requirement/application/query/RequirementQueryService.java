package alm.requirement.application.query;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import alm.requirement.application.query.model.RequirementDetail;
import alm.requirement.application.query.provider.RequirementQueryProvider;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.application.port.TrackerItemLifecyclePort.InheritedEditTarget;
import sk.workingset.application.port.TrackerScopePort;
import sk.workingset.domain.LocalId;

/** Requirement 查询侧应用服务：读模型优先，继承目标回退。 */
public class RequirementQueryService {
    private final RequirementQueryProvider query;
    private final RequirementRepository repository;
    private final TrackerItemLifecyclePort lifecycle;
    private final TrackerScopePort trackerScope;

    public RequirementQueryService(RequirementQueryProvider query, RequirementRepository repository,
                                   TrackerItemLifecyclePort lifecycle, TrackerScopePort trackerScope) {
        this.query = Objects.requireNonNull(query, "query");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.trackerScope = Objects.requireNonNull(trackerScope, "trackerScope");
    }

    @Transactional(readOnly = true)
    public RequirementDetail find(LocalId localId) {
        var persisted = query.find(localId.numericValue());
        if (persisted.isPresent()) {
            if (persisted.get().deleted()) {
                throw new ApplicationFailure(FailureReason.DELETED, localId.value());
            }
            return persisted.get();
        }
        var inherited = lifecycle.findInheritedTarget(localId)
                .map(target -> {
                    trackerScope.requireWorkingSetReady(target.workingSetId());
                    return detail(rebuildInherited(target));
                })
                .orElseGet(() -> detail(load(localId)));
        return inherited;
    }

    private Requirement rebuildInherited(InheritedEditTarget target) {
        Requirement source = load(target.sourceLocalId());
        requireActive(source);
        if (!source.revision().equals(target.sourceRevision())) {
            throw new ApplicationFailure(FailureReason.HISTORY_UNAVAILABLE, target.sourceLocalId().value());
        }
        var identity = target.identity();
        return Requirement.create(identity.localId(), identity.commonId(), identity.trackerId(),
                source.title(), source.description(), identity.revision(), source.version());
    }

    private Requirement load(LocalId localId) {
        return repository.findById(localId)
                .orElseThrow(() -> new ApplicationFailure(FailureReason.NOT_FOUND,
                        "requirement not found: " + localId.value()));
    }

    private static void requireActive(Requirement requirement) {
        if (requirement.deleted()) {
            throw new ApplicationFailure(FailureReason.DELETED, requirement.localId().value());
        }
    }

    private static RequirementDetail detail(Requirement requirement) {
        return new RequirementDetail(
                requirement.localId().numericValue(), requirement.commonId().numericValue(),
                requirement.trackerId().numericValue(), requirement.number(), requirement.title(),
                requirement.description(), requirement.revision().value(), requirement.version().value(),
                requirement.lockVersion().value(), requirement.deleted());
    }
}
