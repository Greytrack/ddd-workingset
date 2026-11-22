package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;

/**
 * LightTrackerItem 的只读摘要；查询侧不返回聚合。
 */
public record LightTrackerItemSummary(Long localId,
                                      Long commonId,
                                      Long trackerId,
                                      Long workingSetId,
                                      Integer revision,
                                      Long sourceLocalId,
                                      Integer sourceRevision,
                                      boolean materialized) {

    public static LightTrackerItemSummary from(LightTrackerItem item) {
        return new LightTrackerItemSummary(item.localId(), item.commonId(), item.trackerId(),
                item.workingSetId(), item.revision(), item.sourceLocalId(), item.sourceRevision(),
                item.materialized());
    }

    public Long effectiveContentSource() {
        return materialized ? localId : sourceLocalId;
    }

    public Integer effectiveContentRevision() {
        return materialized ? revision : sourceRevision;
    }
}
