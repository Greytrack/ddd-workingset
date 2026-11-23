package com.airedgesoft.sharedkernel.workingset.application.common;

import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;

/**
 * 业务 BC 可观察的变体身份证据（D4：LTI 聚合留在 SK，BC 只接收值对象，不接触 SK 聚合）。
 */
public record TargetIdentity(Long localId, Long trackerId, Long commonId,
                             Integer revision, boolean materialized) {

    public static TargetIdentity of(LightTrackerItem item) {
        return new TargetIdentity(item.localId(), item.trackerId(), item.commonId(),
                item.revision(), item.materialized());
    }
}
