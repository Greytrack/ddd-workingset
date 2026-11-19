package sk.workingset.application.query.provider;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.WorkingSetId;

/** LightTrackerItem 的只读摘要；查询侧不返回聚合。 */
public record LightTrackerItemSummary(LocalId localId,
                                      CommonId commonId,
                                      TrackerId trackerId,
                                      WorkingSetId workingSetId,
                                      Revision revision,
                                      LocalId sourceLocalId,
                                      Revision sourceRevision,
                                      boolean materialized) {

    public static LightTrackerItemSummary from(LightTrackerItem item) {
        return new LightTrackerItemSummary(item.localId(), item.commonId(), item.trackerId(),
                item.workingSetId(), item.revision(), item.sourceLocalId(), item.sourceRevision(),
                item.materialized());
    }

    public LocalId effectiveContentSource() {
        return materialized ? localId : sourceLocalId;
    }

    public Revision effectiveContentRevision() {
        return materialized ? revision : sourceRevision;
    }
}
