package sk.workingset.application.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.query.provider.MergeEvidenceReader;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison.ChangeKind;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.MergeResCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 有向比较：按直属父子与 Tracker 来源选择候选，读取双侧证据并生成瞬时差异。
 *
 * <p>比较只读业务内容与 LTI，不写决定；软删对象不进入候选目录（C11），
 * 读取失败按 T4 失败语义区分不可读、已删除与缺失。
 */
@Service
public class MergeComparisonService {

    private final MergeEvidenceReader evidence;
    private final MergeCheckpointQuery checkpoints;

    public MergeComparisonService(MergeEvidenceReader evidence, MergeCheckpointQuery checkpoints) {
        this.evidence = evidence;
        this.checkpoints = checkpoints;
    }

    private static boolean absentOrDeleted(Side side) {
        return side.presence() == Presence.ABSENT || side.presence() == Presence.DELETED;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Comparison compare(WorkingSetId source, WorkingSetId target, TrackerId sourceTracker,
                              TrackerId targetTracker) {
        TrackerPair pair = new TrackerPair(source, sourceTracker, target, targetTracker);
        if (!evidence.candidates(source, target).contains(pair)) {
            throw DomainException.withMessage(MergeResCode.NOT_ALLOWED, "trackers must form a direct derivation pair in the supplied working sets");
        }
        ArrayList<Difference> differences = new ArrayList<Difference>();
        Map<CommonId, LocalId> sources = evidence.familyIds(pair.source());
        Map<CommonId, LocalId> targets = evidence.familyIds(pair.target());
        TreeSet<CommonId> families = new TreeSet<>(Comparator.comparing(CommonId::value));
        families.addAll(sources.keySet());
        families.addAll(targets.keySet());
        for (CommonId family : families) {
            Side left = evidence.read(sources.get(family));
            Side right = evidence.read(targets.get(family));
            if (handled(pair, family, left, right)) {
                continue;
            }
            if (absentOrDeleted(left) && absentOrDeleted(right)) {
                continue;
            }
            ChangeKind kind = Difference.classify(left, right);
            boolean unreadable = left.presence() == Presence.UNREADABLE
                    || right.presence() == Presence.UNREADABLE;
            // 新增/删除只下发存在性差异，不进入字段比较。
            List<String> fields = kind == ChangeKind.MODIFIED ? MergeEvidenceReader.differingFields(left, right) : List.of();
            if (kind == ChangeKind.MODIFIED && !unreadable && fields.isEmpty()) {
                continue;
            }
            differences.add(new Difference(kind, family, pair, left, right, fields));
        }
        return new Comparison(source, target, Instant.now(), List.of(pair), differences);
    }

    private boolean handled(TrackerPair trackers, CommonId family, Side left, Side right) {
        if (left.presence() != Presence.PRESENT || right.presence() != Presence.PRESENT) {
            return false;
        }
        MergeCheckpoint.Key key = new MergeCheckpoint.Key(trackers.source(), trackers.target(), family);
        boolean leftIsA = key.a().equals(trackers.source());
        Revision aRevision = leftIsA ? left.revision() : right.revision();
        Revision bRevision = leftIsA ? right.revision() : left.revision();
        return checkpoints.findLatest(key)
                .filter(checkpoint -> !checkpoint.revoked())
                .filter(checkpoint -> Objects.equals(checkpoint.revisionA(), aRevision)
                        && Objects.equals(checkpoint.revisionB(), bRevision))
                .isPresent();
    }
}
