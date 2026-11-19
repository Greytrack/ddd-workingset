package sk.workingset.application.merge;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import sk.workingset.application.query.provider.TrackerItemQueryProvider;
import sk.workingset.domain.*;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.Comparison.*;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeFailure;
import sk.workingset.domain.merge.MergeFailure.Reason;
import sk.workingset.domain.merge.MergeCheckpointRepository;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.domain.repository.WorkingSetRepository;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * 有向比较：按直属父子与 Tracker 来源选择候选，读取双侧证据并生成瞬时差异。
 *
 * <p>比较只读业务内容与 LTI，不写决定；软删对象不进入候选目录（C11），
 * 读取失败按 T4 失败语义区分不可读、已删除与缺失。
 */
public class MergeComparisonService {
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;
    private final LightTrackerItemQueryProvider itemQueries;
    private final TrackerItemCatalogQueryProvider catalog;
    private final Function<LocalId, TrackerItemQueryProvider> readPorts;
    private final MergeCheckpointRepository store;

    public MergeComparisonService(WorkingSetRepository workingSets, TrackerRepository trackers,
                                  LightTrackerItemRepository items, LightTrackerItemQueryProvider itemQueries,
                                  TrackerItemCatalogQueryProvider catalog,
                                  Function<LocalId, TrackerItemQueryProvider> readPorts, MergeCheckpointRepository store) {
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.items = items;
        this.itemQueries = itemQueries;
        this.catalog = catalog;
        this.readPorts = readPorts;
        this.store = store;
    }

    private static void putFamily(Map<CommonId, LocalId> families, CommonId family, LocalId id) {
        LocalId previous = families.putIfAbsent(family, id);
        if (previous != null && !previous.equals(id)) {
            throw new MergeFailure(Reason.Conflict, "multiple variants in family " + family.value());
        }
    }

    private static Side unreadable(LocalId id, FailureReason reason) {
        return new Side(id, null, null, null, null, null, Presence.UNREADABLE, reason.name(), Map.of());
    }

    private static List<String> differingFields(Side source, Side target) {
        if (source.presence() != Presence.PRESENT || target.presence() != Presence.PRESENT) {
            return List.of();
        }
        TreeSet<String> keys = new TreeSet<>(source.fields().keySet());
        keys.addAll(target.fields().keySet());
        return keys.stream().filter(key -> !java.util.Objects.equals(source.fields().get(key), target.fields().get(key)))
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Comparison compare(WorkingSetId source, WorkingSetId target, TrackerId sourceTracker,
                              TrackerId targetTracker) {
        TrackerPair pair = new TrackerPair(source, sourceTracker, target, targetTracker);
        if (!candidates(source, target).contains(pair)) {
            throw new MergeFailure(Reason.MergeNotAllowed, "trackers must form a direct derivation pair in the supplied working sets");
        }
        ArrayList<Difference> differences = new ArrayList<Difference>();
        Map<CommonId, LocalId> sources = familyIds(pair.source());
        Map<CommonId, LocalId> targets = familyIds(pair.target());
        TreeSet<CommonId> families = new TreeSet<>(java.util.Comparator.comparing(CommonId::value));
        families.addAll(sources.keySet());
        families.addAll(targets.keySet());
        for (CommonId family : families) {
            Side left = read(sources.get(family));
            Side right = read(targets.get(family));
            if (handled(pair, family, left, right)) {
                continue;
            }
            if (left.presence() == Presence.ABSENT && right.presence() == Presence.ABSENT) {
                continue;
            }
            ChangeKind kind = Difference.classify(left, right);
            boolean unreadable = left.presence() == Presence.UNREADABLE
                    || right.presence() == Presence.UNREADABLE;
            // 新增/删除只下发存在性差异，不进入字段比较。
            List<String> fields = kind == ChangeKind.MODIFIED ? differingFields(left, right) : List.of();
            if (kind == ChangeKind.MODIFIED && !unreadable && fields.isEmpty()) {
                continue;
            }
            differences.add(new Difference(kind, family, pair, left, right, fields));
        }
        return new Comparison(source, target, Instant.now(), List.of(pair), differences);
    }

    private boolean handled(Comparison.TrackerPair trackers, CommonId family, Side left, Side right) {
        MergeCheckpoint.Key key = new MergeCheckpoint.Key(trackers.source(), trackers.target(), family);
        boolean leftIsA = key.a().equals(trackers.source());
        sk.workingset.domain.Revision aRevision = leftIsA ? left.revision() : right.revision();
        sk.workingset.domain.Revision bRevision = leftIsA ? right.revision() : left.revision();
        return store.findById(key)
                .filter(checkpoint -> !checkpoint.revoked())
                .filter(checkpoint -> java.util.Objects.equals(checkpoint.revisionA(), aRevision)
                        && java.util.Objects.equals(checkpoint.revisionB(), bRevision))
                .isPresent();
    }

    public List<TrackerPair> candidates(WorkingSetId source, WorkingSetId target) {
        WorkingSet left = workingSet(source);
        WorkingSet right = workingSet(target);
        WorkingSet child = WorkingSetLineageDomainService.childOf(left, right);
        return WorkingSetLineageDomainService.comparableBindings(left, right).stream().flatMap(binding ->
                trackers.findTracker(binding.trackerId()).stream()
                        // The binding must belong to the direct child Working Set; this rejects cross-level
                        // and Shared bindings that happen to carry a sourceId.
                        .filter(tracker -> tracker.workingSetId().equals(child.id()))
                        .filter(tracker -> tracker.sourceId() != null)
                        .filter(tracker -> parentIncludes(left, right, tracker))
                        .map(tracker -> tracker.workingSetId().equals(source)
                                ? new TrackerPair(source, tracker.trackerId(), target, tracker.sourceId())
                                : new TrackerPair(source, tracker.sourceId(), target, tracker.trackerId()))).toList();
    }

    public boolean isMergeBack(WorkingSetId source, WorkingSetId target) {
        return target.equals(workingSet(source).parentWorkingSetId());
    }

    /**
     * The source tracker must be the child's direct sourceId, must belong to the direct parent
     * Working Set, and must be inside that parent's effective range. No ancestor fallback and no
     * sibling pairing: a child's siblings are never candidates for each other.
     */
    private boolean parentIncludes(WorkingSet source, WorkingSet target, Tracker child) {
        if (child.sourceId() == null) {
            return false;
        }
        WorkingSet parent = source.id().equals(target.parentWorkingSetId()) ? source : target;
        return trackers.findTracker(child.sourceId())
                .filter(sourceTracker -> parent.id().equals(sourceTracker.workingSetId()))
                .filter(sourceTracker -> WorkingSetLineageDomainService.isDirectDerivation(child, sourceTracker))
                .filter(sourceTracker -> WorkingSet.DEFAULT_ID.equals(parent.id())
                        || parent.hasTrackerBinding(sourceTracker.trackerId()))
                .isPresent();
    }

    public Difference current(Difference original) {
        TrackerPair pair = original.trackers();
        CommonId family = original.family();
        Side source = read(familyIds(pair.source()).get(family));
        Side target = read(familyIds(pair.target()).get(family));
        return new Difference(family, pair, source, target, differingFields(source, target));
    }

    private Map<CommonId, LocalId> familyIds(TrackerId tracker) {
        Map<CommonId, LocalId> families = new LinkedHashMap<>();
        catalog.identities(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        itemQueries.findInTracker(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        return families;
    }

    private Side read(LocalId id) {
        if (id == null) {
            return Side.absent();
        }
        sk.workingset.domain.LightTrackerItem variant = items.findById(id).orElse(null);
        LocalId contentId = variant == null ? id : variant.effectiveContentSource();
        boolean inherited = variant != null && !variant.materialized();
        TrackerItemQueryProvider port = readPorts.apply(contentId);
        try {
            TrackerItemQueryProvider.ContentEvidence stamp = port.evidence();
            sk.workingset.domain.Revision revision = variant == null || !inherited ? stamp.revision() : variant.revision();
            sk.workingset.domain.Revision contentRevision = inherited ? variant.effectiveContentRevision() : stamp.revision();
            if (!inherited && stamp.deleted()) {
                return Side.absent();
            }
            sk.workingset.application.TrackerItemContent content = inherited ? port.readInheritedAt(contentRevision) : port.readAt(contentRevision);
            return new Side(id, contentId, revision, contentRevision, stamp.version(), stamp.lockVersion(),
                    Presence.PRESENT, null, content.fields());
        } catch (DomainException failure) {
            FailureReason failureReason = FailureReason.of(failure);
            FailureReason reason = inherited && failureReason == FailureReason.NOT_FOUND
                    ? FailureReason.HISTORY_UNAVAILABLE : failureReason;
            return unreadable(id, reason);
        } catch (UnsupportedOperationException failure) {
            return unreadable(id, FailureReason.CAPABILITY_NOT_SUPPORTED);
        }
    }

    private WorkingSet workingSet(WorkingSetId id) {
        return workingSets.findWorkingSet(id).orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, id.value()));
    }
}
