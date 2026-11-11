package sk.workingset.domain;

import java.util.Objects;

/**
 * 最小 Tracker 聚合（首切片仅需非共享派生的身份与来源）。
 *
 * <p>{@code sourceId} 为空表示默认工作集中创建的原始聚合；派生的 Tracker 一定有来源。
 */
public record Tracker(TrackerId trackerId, TrackerId sourceId, WorkingSetId workingSetId) {

    public Tracker {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
    }

    public static Tracker derived(TrackerId trackerId, TrackerId sourceId, WorkingSetId workingSetId) {
        Objects.requireNonNull(sourceId, "sourceId");
        return new Tracker(trackerId, sourceId, workingSetId);
    }

    public boolean isDerived() {
        return sourceId != null;
    }
}
