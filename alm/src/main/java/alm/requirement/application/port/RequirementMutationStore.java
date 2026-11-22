package alm.requirement.application.port;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.application.port.TrackerItemSourceLock;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;

import java.util.Optional;

/**
 * Requirement 写入用例所需的聚合仓储与事务内锁定能力。
 */
public interface RequirementMutationStore extends RequirementRepository {
    Optional<Requirement> lockById(LocalId id);

    Optional<TrackerItemSourceLock.State> lockSourceState(LocalId id);

    Optional<Requirement> lockFamilyInWorkingSet(TrackerId trackerId, CommonId commonId);

    void purge(Requirement requirement);
}
