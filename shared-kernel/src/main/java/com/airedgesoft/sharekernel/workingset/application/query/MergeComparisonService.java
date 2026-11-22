package com.airedgesoft.sharekernel.workingset.application.query;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.application.query.provider.MergeEvidenceReader;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.*;
import com.airedgesoft.sharekernel.workingset.error.MergeResCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    public Comparison compare(Long source, Long target, Long sourceTracker,
                              Long targetTracker) {
        TrackerPair pair = new TrackerPair(source, sourceTracker, target, targetTracker);
        if (!evidence.candidates(source, target).contains(pair)) {
            throw DomainException.withMessage(MergeResCode.NOT_ALLOWED, "trackers must form a direct derivation pair in the supplied working sets");
        }
        ArrayList<Difference> differences = new ArrayList<Difference>();
        Map<Long, Long> sources = evidence.familyIds(pair.source());
        Map<Long, Long> targets = evidence.familyIds(pair.target());
        TreeSet<Long> families = new TreeSet<>(Comparator.comparing(Object::toString));
        families.addAll(sources.keySet());
        families.addAll(targets.keySet());
        for (Long family : families) {
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
        return new Comparison(source, target, System.currentTimeMillis(), List.of(pair), differences);
    }

    private boolean handled(TrackerPair trackers, Long family, Side left, Side right) {
        if (left.presence() != Presence.PRESENT || right.presence() != Presence.PRESENT) {
            return false;
        }
        MergeCheckpoint.Key key = new MergeCheckpoint.Key(trackers.source(), trackers.target(), family);
        boolean leftIsA = key.a().equals(trackers.source());
        Integer aRevision = leftIsA ? left.revision() : right.revision();
        Integer bRevision = leftIsA ? right.revision() : left.revision();
        return checkpoints.findLatest(key)
                .filter(checkpoint -> !checkpoint.revoked())
                .filter(checkpoint -> Objects.equals(checkpoint.revisionA(), aRevision)
                        && Objects.equals(checkpoint.revisionB(), bRevision))
                .isPresent();
    }
}
