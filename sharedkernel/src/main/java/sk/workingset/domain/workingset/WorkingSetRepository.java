package sk.workingset.domain.workingset;

import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

/** WorkingSet 聚合生命周期仓储；查询由 WorkingSetQueryProvider 承担。 */
@Repository
public interface WorkingSetRepository {

    /** 命令侧重建聚合；锁策略不进领域接口。 */
    Optional<WorkingSet> findById(WorkingSetId id);

    /** 创建任务上下文重建：不检查 Preparing。 */
    Optional<WorkingSet> findByIdForCreation(WorkingSetId id);

    void requireReady(WorkingSetId id);

    void save(WorkingSet workingSet);

    void delete(WorkingSet workingSet);

}
