package sk.workingset.domain.merge;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** 瞬时比较结果：双侧工作集、候选 Tracker 对与差异清单；不作为持久化事实。 */
public record Comparison(WorkingSetId source, WorkingSetId target, Instant comparedAt,
                         List<TrackerPair> candidates, List<Difference> differences) {
    public Comparison {
        candidates = List.copyOf(candidates);
        differences = List.copyOf(differences);
    }

    public record TrackerPair(TrackerId source, TrackerId target) {}

    public enum Presence { PRESENT, ABSENT, DELETED, UNREADABLE }

    public record Side(LocalId localId, LocalId contentId, Revision revision, Revision contentRevision,
                       String version, Long lockVersion, Presence presence, String failure,
                       Map<String, Object> fields) {
        public Side { fields = Map.copyOf(fields); }

        public static Side absent() {
            return new Side(null, null, null, null, null, null, Presence.ABSENT, null, Map.of());
        }

        public boolean sameExecutionShape(Side other) {
            return new Shape(localId, revision, contentRevision, version, presence, failure)
                    .equals(new Shape(other.localId, other.revision, other.contentRevision,
                            other.version, other.presence, other.failure));
        }

        private record Shape(LocalId localId, Revision revision, Revision contentRevision,
                             String version, Presence presence, String failure) {}
    }

    /**
     * 瞬时差异。没有客户端可见的 key：条目身份由 MergeRound.Entry.id 与完整 evidence 承担；
     * 本对象只描述“哪一对 Tracker、哪个家族、双侧证据和差异字段”。
     */
    public record Difference(ChangeKind kind, CommonId family, TrackerPair trackers,
                             Side source, Side target, List<String> fields) {
        public Difference {
            fields = List.copyOf(fields);
        }

        public Difference(CommonId family, TrackerPair trackers, Side source, Side target,
                          List<String> fields) {
            this(classify(source, target), family, trackers, source, target, fields);
        }

        public static ChangeKind classify(Side source, Side target) {
            if (source.presence() == Presence.PRESENT && target.presence() == Presence.ABSENT) {
                return ChangeKind.ADDED;
            }
            if (source.presence() == Presence.ABSENT && target.presence() == Presence.PRESENT) {
                return ChangeKind.DELETED;
            }
            return ChangeKind.MODIFIED;
        }
    }

    /** 有向差异的三类可决策结果。 */
    public enum ChangeKind { ADDED, MODIFIED, DELETED }
}
