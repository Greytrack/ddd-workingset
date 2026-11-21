package sk.workingset.application.query.provider;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 的只读查询；列表、分页和存在性判断不进入聚合仓储。
 */
public interface LightTrackerItemQueryProvider {

    /**
     * 按 LocalId 读取摘要；包含软删行，供生命周期同步与类型路由使用。
     */
    Optional<LightTrackerItemSummary> findById(LocalId localId);

    List<LightTrackerItemSummary> findInTracker(TrackerId trackerId);

    List<LightTrackerItemSummary> pageInTracker(TrackerId trackerId, String afterLocalId, int limit);

    boolean existsInTracker(TrackerId trackerId);

    boolean hasFamily(WorkingSetId workingSetId, CommonId commonId);

    boolean hasSourceBackedDependents(LocalId localId);
}
