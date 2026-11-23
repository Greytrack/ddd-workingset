package com.airedgesoft.sharekernel.workingset.domain.workingset;

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
    Optional<WorkingSet> findById(Long id);

    /** 重建聚合并锁定根，用于串行化子工作集创建与父工作集删除。 */
    Optional<WorkingSet> findByIdForUpdate(Long id);

    /** 保护父工作集引用，允许并行创建子工作集及修改父工作集的普通属性。 */
    Optional<WorkingSet> findByIdForChildCreation(Long id);

    /**
     * 创建任务上下文重建：不检查 Preparing。
     */
    Optional<WorkingSet> findByIdForCreation(Long id);

    /** 在准备态任务写入目标范围前保护目标引用，与工作集删除互斥。 */
    Optional<WorkingSet> findByIdForTaskWrite(Long id);

    /**
     * 保存聚合（新增或更新）；绑定变更随根一次保存。
     */
    void save(WorkingSet workingSet);

    /**
     * 永久删除聚合。
     */
    void delete(WorkingSet workingSet);
}
