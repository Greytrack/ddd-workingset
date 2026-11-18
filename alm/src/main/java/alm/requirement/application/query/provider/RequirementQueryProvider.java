package alm.requirement.application.query.provider;

import java.util.Optional;

import alm.requirement.application.query.model.RequirementDetail;

/** Requirement 查询侧只读端口。 */
public interface RequirementQueryProvider {
    Optional<RequirementDetail> find(long localId);
}
