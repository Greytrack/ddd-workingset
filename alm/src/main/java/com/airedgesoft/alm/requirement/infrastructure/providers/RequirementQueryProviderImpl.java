package com.airedgesoft.alm.requirement.infrastructure.providers;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.application.query.provider.RequirementQueryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Requirement MyBatis-Plus 查询适配器；只读。
 */
@Component
@RequiredArgsConstructor
public class RequirementQueryProviderImpl implements RequirementQueryProvider {
    private final RequirementQueryMapper mapper;


    @Override
    public Optional<RequirementDetail> find(long localId) {
        return Optional.ofNullable(mapper.find(localId));
    }

    @Override
    public List<RequirementDetail> findByTracker(long trackerId) {
        return mapper.findByTracker(trackerId);
    }
}
