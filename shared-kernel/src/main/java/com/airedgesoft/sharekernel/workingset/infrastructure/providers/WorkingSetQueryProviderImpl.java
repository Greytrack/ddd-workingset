package com.airedgesoft.sharekernel.workingset.infrastructure.providers;

import com.airedgesoft.sharekernel.workingset.application.query.model.ClassificationSummary;
import com.airedgesoft.sharekernel.workingset.application.query.model.TrackerSummary;
import com.airedgesoft.sharekernel.workingset.application.query.model.WorkingSetBindings;
import com.airedgesoft.sharekernel.workingset.application.query.model.WorkingSetSummary;
import com.airedgesoft.sharekernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharekernel.workingset.infrastructure.providers.mybatis.WorkingSetQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus 查询适配器；只读，不重建 WorkingSet 聚合。
 */
@Component
@RequiredArgsConstructor
public class WorkingSetQueryProviderImpl implements WorkingSetQueryProvider {
    private final WorkingSetQueryMapper mapper;


    @Override
    public Optional<WorkingSetSummary> find(long workingSetId) {
        return Optional.ofNullable(mapper.find(workingSetId));
    }

    @Override
    public boolean isPreparing(long workingSetId) {
        return mapper.isPreparing(workingSetId);
    }

    @Override
    public List<Long> findChildren(long workingSetId) {
        return mapper.findChildren(workingSetId);
    }

    @Override
    public List<Long> findActiveChildren(long workingSetId) {
        return mapper.findActiveChildren(workingSetId);
    }

    @Override
    public List<Long> findAncestors(long workingSetId) {
        return mapper.findAncestors(workingSetId);
    }

    @Override
    public List<TrackerSummary> findTrackers(long workingSetId) {
        return mapper.findTrackers(workingSetId);
    }

    @Override
    public List<ClassificationSummary> findClassifications() {
        return mapper.findClassifications();
    }

    @Override
    public boolean hasWorkingSetBinding(String spaceId) {
        return mapper.hasWorkingSetBinding(Long.parseLong(spaceId));
    }

    @Override
    public boolean hasBindings(long workingSetId) {
        return mapper.hasBindings(workingSetId);
    }

    @Override
    public List<Long> findWorkingSetIdsBoundToTracker(long trackerId) {
        return mapper.findWorkingSetIdsBoundToTracker(trackerId);
    }

    @Override
    public WorkingSetBindings findBindings(long workingSetId) {
        return new WorkingSetBindings(mapper.findSpaceBindings(workingSetId), mapper.findTrackerBindings(workingSetId));
    }
}
