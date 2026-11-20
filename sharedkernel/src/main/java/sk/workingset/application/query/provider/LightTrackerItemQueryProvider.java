package sk.workingset.application.query.provider;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.List;

/** LightTrackerItem 的只读查询；列表、分页和存在性判断不进入聚合仓储。 */
public interface LightTrackerItemQueryProvider {

    List<LightTrackerItemSummary> findInTracker(TrackerId trackerId);

    List<LightTrackerItemSummary> pageInTracker(TrackerId trackerId, String afterLocalId, int limit);

    boolean existsInTracker(TrackerId trackerId);

    boolean hasFamily(WorkingSetId workingSetId, CommonId commonId);

    boolean hasSourceBackedDependents(LocalId localId);
}
