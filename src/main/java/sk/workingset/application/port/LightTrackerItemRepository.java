package sk.workingset.application.port;

import java.util.List;
import java.util.Optional;

import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/**
 * LightTrackerItem 聚合的命令侧存取（T3）。
 */
public interface LightTrackerItemRepository {

    void save(LightTrackerItem item);

    Optional<LightTrackerItem> findById(LocalId localId);

    boolean existsInTracker(TrackerId trackerId);

    List<LightTrackerItem> findInTracker(TrackerId trackerId);

    boolean hasSourceBackedDependents(LocalId id);

    void remove(LocalId id);

    /**
     * 按来源定位候选变体（D-017 消费时确定目标）：返回 {@code sourceLocalId}/{@code sourceRevision}
     * 匹配的变体；是否已物化由调用方判断（不得在仓储层暗含业务判断）。
     */
    List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision);
}
