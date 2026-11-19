package sk.workingset.application.query.provider;

import sk.workingset.application.query.model.BindingsView;
import sk.workingset.application.query.model.ClassificationSummary;
import sk.workingset.application.query.model.TrackerSummary;
import sk.workingset.application.query.model.WorkingSetSummary;

import java.util.List;
import java.util.Optional;

/**
 * Working Set 查询侧端口；实现必须只读。
 */
public interface WorkingSetQueryProvider {

    Optional<WorkingSetSummary> find(long workingSetId);

    boolean isPreparing(long workingSetId);

    List<Long> findChildren(long workingSetId);

    List<Long> findAncestors(long workingSetId);

    List<TrackerSummary> findTrackers(long workingSetId);

    List<ClassificationSummary> findClassifications();

    BindingsView findBindings(long workingSetId);

    List<Long> findWorkingSetsBoundToSpace(String spaceId);
}
