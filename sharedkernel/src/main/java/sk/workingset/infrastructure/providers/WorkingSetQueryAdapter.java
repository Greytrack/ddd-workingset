package sk.workingset.infrastructure.providers;

import java.util.List;
import java.util.Optional;

import sk.workingset.application.query.model.BindingsView;
import sk.workingset.application.query.model.ClassificationSummary;
import sk.workingset.application.query.model.TrackerSummary;
import sk.workingset.application.query.model.WorkingSetSummary;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.infrastructure.providers.mybatis.WorkingSetQueryMapper;

/** MyBatis-Plus 查询适配器；只读，不重建 WorkingSet 聚合。 */
public final class WorkingSetQueryAdapter implements WorkingSetQueryProvider {
    private final WorkingSetQueryMapper mapper;

    public WorkingSetQueryAdapter(WorkingSetQueryMapper mapper) {
        this.mapper = mapper;
    }

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
        return mapper.findTrackers(workingSetId);
    }

    @Override
    public List<ClassificationSummary> findClassifications() {
        return mapper.findClassifications();
    }

    @Override
    public BindingsView findBindings(long workingSetId) {
        return new BindingsView(mapper.findSpaceBindings(workingSetId),
                mapper.findTrackerBindings(workingSetId));
    }

    @Override
    public boolean hasTrackerBinding(long workingSetId, long trackerId) {
        return mapper.findTrackerBinding(workingSetId, trackerId) != null;
    }
}
