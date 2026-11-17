package sk.workingset.application.event;

import java.time.Instant;
import java.util.Objects;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/**
 * 业务对象被永久删除的事实（SK 定义、业务 BC 发布）。
 *
 * <p>SK 据此移除对应 LightTrackerItem；事件只携带公共身份，不携带业务字段。
 * 业务 BC 在永久删除提交后发布，SK 侧处理必须幂等。
 *
 * @param requirementId 被永久删除的业务对象身份
 * @param trackerId     删除时所属 Tracker
 * @param occurredAt    事件发生时间
 */
public record RequirementPurged(LocalId requirementId, TrackerId trackerId, Instant occurredAt) {

    public RequirementPurged {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
