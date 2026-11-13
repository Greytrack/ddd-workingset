package sk.workingset.domain.merge;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** 冻结比较：token、双侧工作集、候选 Tracker 对与差异清单；只读、可追溯。 */
public record Comparison(String token, WorkingSetId source, WorkingSetId target, Instant comparedAt,
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

    public record Difference(String key, CommonId family, TrackerPair trackers, Side source, Side target,
                             List<String> fields) {
        public Difference { fields = List.copyOf(fields); }
    }
}
