package com.airedgesoft.sharedkernel.workingset.domain.merge;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 瞬时比较结果：双侧工作集、候选 Tracker 对与差异清单；不作为持久化事实。
 */
@ValueObject
public record Comparison(@JsonFormat(shape = JsonFormat.Shape.STRING) Long source, @JsonFormat(shape = JsonFormat.Shape.STRING) Long target, long comparedAt,
                         List<TrackerPair> candidates, List<Difference> differences) {
    public Comparison {
        candidates = List.copyOf(candidates);
        differences = List.copyOf(differences);
    }

    public enum Presence {PRESENT, ABSENT, DELETED, UNREADABLE}

    /**
     * 有向差异的三类可决策结果。
     */
    public enum ChangeKind {ADDED, MODIFIED, DELETED}

    /**
     * 有向比较对：source/target WorkingSet 是本次角色上下文，不是父子谱系方向。
     * TrackerId 与 WorkingSetId 一起构成可审计的候选对/差异身份；Checkpoint 仍只使用 TrackerId。
     */
    @ValueObject
    public record TrackerPair(@JsonFormat(shape = JsonFormat.Shape.STRING) Long sourceWorkingSetId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long source,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) Long targetWorkingSetId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long target) {
        public TrackerPair {
            Objects.requireNonNull(sourceWorkingSetId, "sourceWorkingSetId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(targetWorkingSetId, "targetWorkingSetId");
            Objects.requireNonNull(target, "target");
            if (sourceWorkingSetId <= 0 || source <= 0 || targetWorkingSetId <= 0 || target <= 0) {
                throw new IllegalArgumentException("Tracker pair IDs must be positive");
            }
        }
    }

    @ValueObject
    public record Side(@JsonFormat(shape = JsonFormat.Shape.STRING) Long localId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long contentId, Integer revision, Integer contentRevision,
                       String version, Long lockVersion, Presence presence, String failure,
                       Map<String, Object> fields) {
        public Side {
            if ((localId != null && localId <= 0) || (contentId != null && contentId <= 0)) {
                throw new IllegalArgumentException("Evidence IDs must be positive");
            }
            if ((revision != null && revision < 0) || (contentRevision != null && contentRevision < 0)
                    || (lockVersion != null && lockVersion < 0)) {
                throw new IllegalArgumentException("Evidence revisions and lock version must not be negative");
            }
            fields = Map.copyOf(fields);
        }

        public static Side absent() {
            return new Side(null, null, null, null, null, null, Presence.ABSENT, null, Map.of());
        }

    }

    /**
     * 瞬时差异。没有客户端可见的 key：条目身份由 MergeRound.Entry.id 与完整 evidence 承担；
     * 本对象只描述“哪一对 Tracker、哪个家族、双侧证据和差异字段”。
     */
    @ValueObject
    public record Difference(ChangeKind kind, @JsonFormat(shape = JsonFormat.Shape.STRING) Long family, TrackerPair trackers,
                             Side source, Side target, List<String> fields) {
        public Difference {
            if (Objects.requireNonNull(family, "family") <= 0) {
                throw new IllegalArgumentException("Family ID must be positive");
            }
            fields = List.copyOf(fields);
        }

        public Difference(Long family, TrackerPair trackers, Side source, Side target,
                          List<String> fields) {
            this(classify(source, target), family, trackers, source, target, fields);
        }

        public static ChangeKind classify(Side source, Side target) {
            if (source.presence() == Presence.PRESENT && (target.presence() == Presence.ABSENT || target.presence() == Presence.DELETED)) {
                return ChangeKind.ADDED;
            }
            if ((source.presence() == Presence.ABSENT || source.presence() == Presence.DELETED) && target.presence() == Presence.PRESENT) {
                return ChangeKind.DELETED;
            }
            return ChangeKind.MODIFIED;
        }
    }
}
