package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 聚合的命令侧存取（T3）。
 */
@Repository
public interface LightTrackerItemRepository {

    void save(LightTrackerItem item);

    Optional<LightTrackerItem> findById(LocalId localId);

    boolean existsInTracker(TrackerId trackerId);

    List<LightTrackerItem> findInTracker(TrackerId trackerId);

    List<LightTrackerItem> pageInTracker(TrackerId trackerId, String afterLocalId, int limit);

    /** Registers the derived index and its public summary as one unit inside the task's item savepoint. */
    boolean insertIfFamilyAbsent(LightTrackerItem item);

    boolean hasFamily(WorkingSetId workingSetId, CommonId commonId);

    boolean hasSourceBackedDependents(LocalId id);

    void remove(LocalId id);

    /**
     * 按来源定位候选变体（D-017 消费时确定目标）：返回 {@code sourceLocalId}/{@code sourceRevision}
     * 匹配的变体；是否已物化由调用方判断（不得在仓储层暗含业务判断）。
     */
    List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision);
}
