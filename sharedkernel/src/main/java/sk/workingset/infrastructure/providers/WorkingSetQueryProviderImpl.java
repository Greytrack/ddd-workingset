package sk.workingset.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sk.RequestThreadHolder;
import sk.workingset.application.query.model.BindingsView;
import sk.workingset.application.query.model.ClassificationSummary;
import sk.workingset.application.query.model.TrackerSummary;
import sk.workingset.application.query.model.WorkingSetSummary;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.infrastructure.providers.mybatis.WorkingSetQueryMapper;

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
    public List<Long> findAncestors(long workingSetId) {
        return mapper.findAncestors(workingSetId);
    }

    @Override
    public List<TrackerSummary> findTrackers(long workingSetId) {
        return mapper.findTrackers(workingSetId, RequestThreadHolder.getWorkspace());
    }

    @Override
    public List<ClassificationSummary> findClassifications() {
        return mapper.findClassifications();
    }

    @Override
    public BindingsView findBindings(long workingSetId) {
        Long workspace = RequestThreadHolder.getWorkspace();
        return new BindingsView(mapper.findSpaceBindings(workingSetId, workspace),
                mapper.findTrackerBindings(workingSetId, workspace));
    }

    @Override
    public boolean hasTrackerBinding(long workingSetId, long trackerId) {
        return mapper.hasTrackerBinding(workingSetId, trackerId, RequestThreadHolder.getWorkspace());
    }
}
