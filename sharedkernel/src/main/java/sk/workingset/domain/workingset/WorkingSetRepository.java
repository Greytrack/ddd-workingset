package sk.workingset.domain.workingset;

import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

/** WorkingSet 聚合生命周期仓储；查询由 WorkingSetQueryProvider 承担。 */
@Repository
public interface WorkingSetRepository {

    /** 命令侧重建聚合；锁策略不进领域接口。 */
    Optional<WorkingSet> findById(WorkingSetId id);

    /**
     * 创建任务上下文重建：不检查 Preparing。
     *
     * @deprecated 待迁移到 WorkingSetQueryProvider / 任务专用 Store；当前仅为兼容保留。
     */
    @Deprecated(since = "2026-11-20", forRemoval = false)
    Optional<WorkingSet> findByIdForCreation(WorkingSetId id);

    /**
     * @deprecated 待迁移到 WorkingSetQueryProvider#isPreparing；当前仅为兼容保留。
     */
    @Deprecated(since = "2026-11-20", forRemoval = false)
    void requireReady(WorkingSetId id);

    void save(WorkingSet workingSet);

    void delete(WorkingSet workingSet);
}
