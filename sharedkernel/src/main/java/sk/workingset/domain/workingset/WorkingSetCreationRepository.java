package sk.workingset.domain.workingset;

import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

@Repository
public interface WorkingSetCreationRepository {
    /** 保存整个任务；新任务若未取得幂等或活跃任务席位则返回 false。 */
    boolean save(WorkingSetCreation creation);

    Optional<WorkingSetCreation> find(String id, boolean lock);

    Optional<WorkingSetCreation> findByTarget(WorkingSetId target);

    Optional<WorkingSetCreation> findByRequest(String requestId);

    Optional<WorkingSetCreation> findActiveExtension(WorkingSetId target);

}
