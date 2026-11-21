package sk.workingset.domain.workingset;

import org.jmolecules.ddd.annotation.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * WorkingSetCreation 任务仓储：保存任务、按身份/幂等键/目标工作集定位，供创建与扩展 LRO 逐步推进。
 */
@Repository
public interface WorkingSetCreationRepository {
    /**
     * Time used for HEAD, on the same clock as Item creation timestamps.
     */
    Instant acceptanceTime();

    /**
     * 保存整个任务；新任务若未取得幂等或活跃任务席位则返回 false。
     */
    boolean save(WorkingSetCreation creation);

    /**
     * 按任务 id 读取。
     */
    Optional<WorkingSetCreation> find(String id);

    /**
     * 按任务 id 加锁读取，供逐步推进任务。
     */
    Optional<WorkingSetCreation> findForUpdate(String id);

    /**
     * 按目标工作集定位创建任务。
     */
    Optional<WorkingSetCreation> findByTarget(WorkingSetId target);

    /**
     * 按请求幂等键定位任务。
     */
    Optional<WorkingSetCreation> findByRequest(String requestId);

    /**
     * 按目标工作集定位活动中的扩展任务。
     */
    Optional<WorkingSetCreation> findActiveExtension(WorkingSetId target);

}
