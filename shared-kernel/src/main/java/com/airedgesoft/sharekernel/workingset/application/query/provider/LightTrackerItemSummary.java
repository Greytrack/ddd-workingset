package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;

/**
 * LightTrackerItem 的只读摘要；查询侧不返回聚合。
 */
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
