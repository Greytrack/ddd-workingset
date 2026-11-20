package sk.workingset.domain.workingset;

import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

/**
 * WorkingSet 聚合生命周期仓储；查询由 WorkingSetQueryProvider 承担。
 */
@Repository
public interface WorkingSetRepository {

    /**
     * 命令侧重建普通入口可访问的聚合；创建任务使用专用查询。
     */
    Optional<WorkingSet> findById(WorkingSetId id);

    /**
     * 创建任务上下文重建：不检查 Preparing。
     */
    Optional<WorkingSet> findByIdForCreation(WorkingSetId id);

    /**
     * 保存聚合（新增或更新）；绑定变更随根一次保存。
     */
    void save(WorkingSet workingSet);

    /**
     * 永久删除聚合。
     */
    void delete(WorkingSet workingSet);
}
