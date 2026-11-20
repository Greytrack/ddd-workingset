package sk.workingset.application.query;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison.ChangeKind;

/** Read model for the latest confirmation across merge rounds. */
public record MergeCheckpoint(Key key, long checkpointId, Revision revisionA, Revision revisionB,
                              LocalId localIdA, LocalId localIdB, String roundId, ChangeKind changeKind,
                              Direction direction, Action action, boolean revoked, String operator) {
    public enum Direction {A_TO_B, B_TO_A}

    public enum Action {ACCEPTED, IGNORED}

    /**
     * 方向无关键：A/B 按 TrackerId 数值排序，保证同一对 Tracker 只有一种表示。
     */
    public record Key(TrackerId a, TrackerId b, CommonId family) {
        public Key {
            if (a.numericValue() > b.numericValue()) {
                TrackerId low = a;
                a = b;
                b = low;
            }
        }
    }
}
