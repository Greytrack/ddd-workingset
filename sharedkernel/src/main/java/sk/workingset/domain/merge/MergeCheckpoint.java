package sk.workingset.domain.merge;

import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.ValueObject;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.merge.Comparison.ChangeKind;

/**
 * 「已处理」锚点：两侧 Tracker 分支 + 家族的确认/撤销事实。
 *
 * <p>方向只是属性；身份由规范化后的 Tracker 分支对与家族决定，WorkingSet 不参与。
 * decision_version 用于防止晚到确认/撤销覆盖更新的事实。
 */
@Entity
public record MergeCheckpoint(Key key, long decisionVersion, Revision revisionA, Revision revisionB,
                              LocalId localIdA, LocalId localIdB, String roundId, ChangeKind changeKind,
                              Direction direction, boolean revoked, String operator) {
    public enum Direction {A_TO_B, B_TO_A}

    /**
     * 方向无关键：A/B 按 TrackerId 数值排序，保证同一对 Tracker 只有一种表示。
     */
    @ValueObject
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
