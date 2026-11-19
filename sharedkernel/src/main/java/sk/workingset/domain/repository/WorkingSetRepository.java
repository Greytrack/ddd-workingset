package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

import java.util.List;
import java.util.Optional;

/**
 * WorkingSet 聚合存取、绑定关系与谱系查询（子级/祖先）。
 */
@Repository
public interface WorkingSetRepository {
    Optional<WorkingSet> findWorkingSet(WorkingSetId id);

    Optional<WorkingSet> lockWorkingSet(WorkingSetId id);

    Optional<WorkingSet> lockWorkingSetForCreation(WorkingSetId id);

    /**
     * 任务上下文只读：不检查 Preparing，仅供 WorkingSetCreationService 使用。
     */
    Optional<WorkingSet> findWorkingSetForCreation(WorkingSetId id);

    void requireReady(WorkingSetId id);

    default boolean save(WorkingSet workingSet) {
        return save(workingSet, null);
    }

    /** 保存新建或持锁重建的聚合及成员；新建身份冲突时不覆盖现有状态。 */
    boolean save(WorkingSet workingSet, Long actor);

    List<WorkingSetId> findChildren(WorkingSetId id);

    List<WorkingSetId> findAncestors(WorkingSetId id);


    void deleteBindingsToTracker(TrackerId trackerId);


    List<WorkingSetId> findWorkingSetsBoundToSpace(String spaceId);

    void deleteWorkingSet(WorkingSetId id);
}
