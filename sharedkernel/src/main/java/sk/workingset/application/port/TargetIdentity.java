package sk.workingset.application.port;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

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
