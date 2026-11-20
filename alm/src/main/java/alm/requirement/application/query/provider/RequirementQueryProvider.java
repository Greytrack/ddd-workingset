package alm.requirement.application.query.provider;

import alm.requirement.application.query.model.RequirementDetail;

import java.util.List;
import java.util.Optional;

/** Requirement 查询侧只读端口。 */
public interface RequirementQueryProvider {
    Optional<RequirementDetail> find(long localId);

    List<RequirementDetail> findByTracker(long trackerId, long workingSetId);
}
