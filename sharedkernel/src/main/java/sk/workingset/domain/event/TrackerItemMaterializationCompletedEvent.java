package sk.workingset.domain.event;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;

import java.util.Objects;

/**
 * ALM 对目标变体实例化请求的最终处理结果。
 */
public record TrackerItemMaterializationCompletedEvent(long eventId,
                                                       LocalId targetLocalId,
                                                       Outcome outcome,
                                                       LocalId businessRef,
                                                       Revision revision,
                                                       LockVersion beforeLockVersion,
                                                       LockVersion afterLockVersion,
                                                       String failure) {

    public TrackerItemMaterializationCompletedEvent {
        Objects.requireNonNull(targetLocalId, "targetLocalId");
        Objects.requireNonNull(outcome, "outcome");
    }

    public enum Outcome {MATERIALIZED, FAILED}
}
