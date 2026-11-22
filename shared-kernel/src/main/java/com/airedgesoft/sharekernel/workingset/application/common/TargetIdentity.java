package com.airedgesoft.sharekernel.workingset.application.common;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;

/**
 * 业务 BC 可观察的变体身份证据（D4：LTI 聚合留在 SK，BC 只接收值对象，不接触 SK 聚合）。
 */
public record TargetIdentity(LocalId localId, TrackerId trackerId, CommonId commonId,
                             Revision revision, boolean materialized) {

    public static TargetIdentity of(LightTrackerItem item) {
        return new TargetIdentity(item.localId(), item.trackerId(), item.commonId(),
                item.revision(), item.materialized());
    }
}
