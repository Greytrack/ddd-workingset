package alm.requirement.infrastructure.providers;

import java.util.List;
import java.util.Optional;

import alm.requirement.application.query.model.RequirementDetail;
import alm.requirement.application.query.provider.RequirementQueryProvider;

/** Requirement MyBatis-Plus 查询适配器；只读。 */
public final class RequirementQueryProviderImpl implements RequirementQueryProvider {
    private final RequirementQueryMapper mapper;

    public RequirementQueryProviderImpl(RequirementQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RequirementDetail> find(long localId) {
        return Optional.ofNullable(mapper.find(localId));
    }

    @Override
    public List<RequirementDetail> findByTracker(long trackerId, long workingSetId) {
        return mapper.findByTracker(trackerId, workingSetId);
    }
}
