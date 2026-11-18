package sk.workingset.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.domain.merge.MergeRepository;
import sk.workingset.application.port.RequirementCatalog;
import sk.workingset.application.port.TrackerItemReadPort;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSetLineagePolicy;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.Comparison.ChangeKind;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeFailure;
import sk.workingset.domain.merge.MergeFailure.Reason;

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
    private final RequirementCatalog catalog;
    private final Function<LocalId, TrackerItemReadPort> readPorts;
    private final MergeRepository store;

    public MergeComparisonService(WorkingSetRepository workingSets, TrackerRepository trackers,
                                  LightTrackerItemRepository items, RequirementCatalog catalog,
                                  Function<LocalId, TrackerItemReadPort> readPorts, MergeRepository store) {
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.items = items;
        this.catalog = catalog;
        this.readPorts = readPorts;
        this.store = store;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Comparison compare(WorkingSetId source, WorkingSetId target, TrackerId sourceTracker,
                              TrackerId targetTracker, Caller caller) {
        var pair = new TrackerPair(source, sourceTracker, target, targetTracker);
        if (!candidates(source, target).contains(pair)) {
            throw new MergeFailure(Reason.MergeNotAllowed, "trackers must form a direct derivation pair in the supplied working sets");
        }
        var differences = new ArrayList<Difference>();
        var sources = familyIds(pair.source());
        var targets = familyIds(pair.target());
        var families = new TreeSet<>(java.util.Comparator.comparing(CommonId::value));
        families.addAll(sources.keySet());
        families.addAll(targets.keySet());
        for (var family : families) {
            Side left = read(sources.get(family), caller);
            Side right = read(targets.get(family), caller);
            if (handled(pair, family, left, right)) {
                continue;
            }
            if (left.presence() == Presence.ABSENT && right.presence() == Presence.ABSENT) {
                continue;
            }
            var kind = Difference.classify(left, right);
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
        var key = new MergeCheckpoint.Key(trackers.source(), trackers.target(), family);
        boolean leftIsA = key.a().equals(trackers.source());
        var aRevision = leftIsA ? left.revision() : right.revision();
        var bRevision = leftIsA ? right.revision() : left.revision();
        return store.findCheckpoint(key)
                .filter(checkpoint -> !checkpoint.revoked())
                .filter(checkpoint -> java.util.Objects.equals(checkpoint.revisionA(), aRevision)
                        && java.util.Objects.equals(checkpoint.revisionB(), bRevision))
                .isPresent();
    }

    public List<TrackerPair> candidates(WorkingSetId source, WorkingSetId target) {
        var left = workingSet(source);
        var right = workingSet(target);
        var child = WorkingSetLineagePolicy.childOf(left, right);
        return WorkingSetLineagePolicy.comparableBindings(left, right).stream().flatMap(binding ->
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
                .filter(sourceTracker -> WorkingSetLineagePolicy.isDirectDerivation(child, sourceTracker))
                .filter(sourceTracker -> WorkingSet.DEFAULT_ID.equals(parent.id())
                        || parent.hasTrackerBinding(sourceTracker.trackerId()))
                .isPresent();
    }

    public Difference current(Difference original, Caller caller) {
        var pair = original.trackers();
        var family = original.family();
        Side source = read(familyIds(pair.source()).get(family), caller);
        Side target = read(familyIds(pair.target()).get(family), caller);
        return new Difference(family, pair, source, target, differingFields(source, target));
    }

    private Map<CommonId, LocalId> familyIds(TrackerId tracker) {
        Map<CommonId, LocalId> families = new LinkedHashMap<>();
        catalog.identities(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        items.findInTracker(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        return families;
    }

    private static void putFamily(Map<CommonId, LocalId> families, CommonId family, LocalId id) {
        var previous = families.putIfAbsent(family, id);
        if (previous != null && !previous.equals(id)) {
            throw new MergeFailure(Reason.Conflict, "multiple variants in family " + family.value());
        }
    }

    private Side read(LocalId id, Caller caller) {
        if (id == null) {
            return Side.absent();
        }
        var variant = items.findById(id).orElse(null);
        LocalId contentId = variant == null ? id : variant.effectiveContentSource();
        boolean inherited = variant != null && !variant.materialized();
        var port = readPorts.apply(contentId);
        try {
            if (!port.authorize(Operation.READ, caller)) {
                return unreadable(null, FailureReason.DENIED);
            }
            if (!port.capabilities().comparable()) {
                return unreadable(id, FailureReason.CAPABILITY_NOT_SUPPORTED);
            }
            var stamp = port.evidence();
            var revision = variant == null || !inherited ? stamp.revision() : variant.revision();
            var contentRevision = inherited ? variant.effectiveContentRevision() : stamp.revision();
            if (!inherited && stamp.deleted()) {
                return Side.absent();
            }
            var content = inherited ? port.readInheritedAt(contentRevision) : port.readAt(contentRevision);
            return new Side(id, contentId, revision, contentRevision, stamp.version(), stamp.lockVersion(),
                    Presence.PRESENT, null, content.fields());
        } catch (ApplicationFailure failure) {
            var reason = inherited && failure.reason() == FailureReason.NOT_FOUND
                    ? FailureReason.HISTORY_UNAVAILABLE : failure.reason();
            return unreadable(id, reason);
        } catch (UnsupportedOperationException failure) {
            return unreadable(id, FailureReason.CAPABILITY_NOT_SUPPORTED);
        }
    }

    public void assertWriteCapability(Difference difference, boolean create, Caller caller) {
        LocalId capabilityId = create ? difference.source().localId() : difference.target().localId();
        var port = readPorts.apply(capabilityId);
        if (!port.authorize(Operation.WRITE, caller)) {
            throw new ApplicationFailure(FailureReason.DENIED, capabilityId.value());
        }
        var capability = port.capabilities();
        if (!capability.mergeable() || (create && !capability.creatable())) {
            throw new ApplicationFailure(FailureReason.CAPABILITY_NOT_SUPPORTED, capabilityId.value());
        }
    }

    private static Side unreadable(LocalId id, FailureReason reason) {
        return new Side(id, null, null, null, null, null, Presence.UNREADABLE, reason.name(), Map.of());
    }

    private static List<String> differingFields(Side source, Side target) {
        if (source.presence() != Presence.PRESENT || target.presence() != Presence.PRESENT) {
            return List.of();
        }
        var keys = new TreeSet<>(source.fields().keySet());
        keys.addAll(target.fields().keySet());
        return keys.stream().filter(key -> !java.util.Objects.equals(source.fields().get(key), target.fields().get(key)))
                .toList();
    }

    private WorkingSet workingSet(WorkingSetId id) {
        return workingSets.findWorkingSet(id).orElseThrow(() -> new ApplicationFailure(FailureReason.NOT_FOUND, id.value()));
    }
}
