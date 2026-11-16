package sk.workingset.domain.merge;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.merge.Comparison.ChangeKind;

/**
 * 「已处理」锚点：两侧 Tracker 分支 + 家族的确认/撤销事实。
 *
 * <p>方向只是属性；身份由规范化后的 Tracker 分支对与家族决定，WorkingSet 不参与。
 * decision_version 用于防止晚到确认/撤销覆盖更新的事实。
 */
public record MergeCheckpoint(Key key, long decisionVersion, Revision revisionA, Revision revisionB,
                              LocalId localIdA, LocalId localIdB, String roundId, ChangeKind changeKind,
                              Direction direction, boolean revoked, String operator) {
    public enum Direction { A_TO_B, B_TO_A }

    /** 方向无关键：A/B 按 TrackerId 字符串排序，保证同一对 Tracker 只有一种表示。 */
    public record Key(TrackerId a, TrackerId b, CommonId family) {
        public Key {
            if (a.value().compareTo(b.value()) > 0) {
                var low = a;
                a = b;
                b = low;
            }
        }
    }
}
