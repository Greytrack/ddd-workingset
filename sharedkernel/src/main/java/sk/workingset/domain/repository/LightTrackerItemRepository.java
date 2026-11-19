package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

import java.util.List;
import java.util.Optional;

/** LightTrackerItem 聚合的命令侧存取。查询职责由 LightTrackerItemQueryProvider 承担。 */
@Repository
public interface LightTrackerItemRepository {

    void save(LightTrackerItem item);

    Optional<LightTrackerItem> findById(LocalId localId);

    /**
     * COW 命令侧领域 finder：按来源切点定位尚未物化的目标聚合。
     */
    List<LightTrackerItem> findSourceBackedTargets(LocalId sourceLocalId, Revision sourceRevision);

    void remove(LocalId id);
}
