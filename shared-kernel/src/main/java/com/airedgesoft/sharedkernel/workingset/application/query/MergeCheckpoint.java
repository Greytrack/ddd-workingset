package com.airedgesoft.sharedkernel.workingset.application.query;

import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.ChangeKind;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Read model for the latest confirmation across merge rounds.
 */
public record MergeCheckpoint(Key key, long checkpointId, Integer revisionA, Integer revisionB,
                              Long localIdA, Long localIdB, String roundId, ChangeKind changeKind,
                              Direction direction, Action action, boolean revoked, String operator) {
    public enum Direction {A_TO_B, B_TO_A}

    public enum Action {ACCEPTED, IGNORED}

    /**
     * 方向无关键：A/B 按 TrackerId 数值排序，保证同一对 Tracker 只有一种表示。
     */
    public record Key(@JsonFormat(shape = JsonFormat.Shape.STRING) Long a, @JsonFormat(shape = JsonFormat.Shape.STRING) Long b, @JsonFormat(shape = JsonFormat.Shape.STRING) Long family) {
        public Key {
            if (a > b) {
                Long low = a;
                a = b;
                b = low;
            }
        }
    }
}
