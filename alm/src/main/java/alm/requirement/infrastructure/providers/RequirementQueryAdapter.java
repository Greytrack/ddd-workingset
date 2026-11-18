package alm.requirement.infrastructure.providers;

import java.util.Optional;

import alm.requirement.application.query.model.RequirementDetail;
import alm.requirement.application.query.provider.RequirementQueryProvider;
import alm.requirement.infrastructure.providers.mybatis.RequirementQueryMapper;

/** Requirement MyBatis-Plus 查询适配器；只读。 */
public final class RequirementQueryAdapter implements RequirementQueryProvider {
    private final RequirementQueryMapper mapper;

    public RequirementQueryAdapter(RequirementQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RequirementDetail> find(long localId) {
        return Optional.ofNullable(mapper.find(localId));
    }
}
