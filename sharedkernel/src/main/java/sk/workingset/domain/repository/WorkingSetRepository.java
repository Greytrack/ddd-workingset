package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

import java.util.Optional;

/** WorkingSet 聚合生命周期仓储；查询由 WorkingSetQueryProvider 承担。 */
@Repository
public interface WorkingSetRepository {

    /** 命令侧重建并隐式加锁；不向领域接口暴露 lock 参数。 */
    Optional<WorkingSet> findById(WorkingSetId id);

    /** 命令侧重建并加行锁。 */
    Optional<WorkingSet> findByIdForUpdate(WorkingSetId id);

    /** 创建任务上下文重建：不检查 Preparing。 */
    Optional<WorkingSet> findByIdForCreation(WorkingSetId id);

    void requireReady(WorkingSetId id);

    void save(WorkingSet workingSet);

    void delete(WorkingSet workingSet);

}
