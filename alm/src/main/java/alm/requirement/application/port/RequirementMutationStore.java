package alm.requirement.application.port;

import java.util.Optional;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.tracker.domain.TrackerId;

/** Requirement 写入用例所需的聚合仓储与事务内锁定能力。 */
public interface RequirementMutationStore extends RequirementRepository {
    Optional<Requirement> lockById(LocalId id);

    Optional<Requirement> lockFamilyInWorkingSet(TrackerId trackerId, CommonId commonId);

    void purge(Requirement requirement);
}
