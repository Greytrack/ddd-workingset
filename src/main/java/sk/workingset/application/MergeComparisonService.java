package sk.workingset.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.MergeStore;
import sk.workingset.application.port.RequirementCatalog;
import sk.workingset.application.port.TrackerItemReadPort;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSetLineagePolicy;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeFailure;
import sk.workingset.domain.merge.MergeFailure.Reason;

/**
 * 有向比较：按直属父子与 Tracker 来源选择候选，读取双侧证据并生成冻结差异。
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
    private final MergeStore store;

    public MergeComparisonService(WorkingSetRepository workingSets, TrackerRepository trackers,
                                  LightTrackerItemRepository items, RequirementCatalog catalog,
                                  Function<LocalId, TrackerItemReadPort> readPorts, MergeStore store) {
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.items = items;
        this.catalog = catalog;
        this.readPorts = readPorts;
        this.store = store;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Comparison compare(WorkingSetId source, WorkingSetId target, TrackerId selected, Caller caller) {
        var candidates = candidates(source, target);
        if (selected != null) {
            candidates = candidates.stream()
                    .filter(pair -> selected.equals(pair.source()) || selected.equals(pair.target())).toList();
            if (candidates.isEmpty()) {
                throw new MergeFailure(Reason.MergeNotAllowed, "tracker is outside the comparable range");
            }
        }
        var differences = new ArrayList<Difference>();
        for (var pair : candidates) {
            var sources = familyIds(pair.source());
            var targets = familyIds(pair.target());
            var families = new TreeSet<CommonId>(java.util.Comparator.comparing(CommonId::value));
            families.addAll(sources.keySet());
            families.addAll(targets.keySet());
            for (var family : families) {
                Side left = read(sources.get(family), caller);
                Side right = read(targets.get(family), caller);
                List<String> fields = differingFields(left, right);
                boolean unreadable = left.presence() == Presence.UNREADABLE || right.presence() == Presence.UNREADABLE;
                boolean differs = unreadable || left.presence() != right.presence() || !fields.isEmpty();
                if (differs && (unreadable || !handled(source, target, family, left, right))) {
                    differences.add(new Difference(Integer.toString(differences.size() + 1), family, pair, left, right,
                            fields));
                }
            }
        }
        var comparison = new Comparison(UUID.randomUUID().toString(), source, target, Instant.now(),
                candidates, differences);
        store.insertComparison(comparison);
        return comparison;
    }

    private boolean handled(WorkingSetId source, WorkingSetId target, CommonId family, Side left, Side right) {
        return store.findCheckpoint(new MergeCheckpoint.Key(source, target, family))
                .filter(checkpoint -> !checkpoint.revoked())
                .filter(checkpoint -> java.util.Objects.equals(checkpoint.sourceRevision(), left.revision())
                        && java.util.Objects.equals(checkpoint.targetRevision(), right.revision()))
                .isPresent();
    }

    public Comparison find(String token) {
        return store.findComparison(token)
                .orElseThrow(() -> new MergeFailure(Reason.ComparisonNotFound, token));
    }

    List<TrackerPair> candidates(WorkingSetId source, WorkingSetId target) {
        var left = workingSet(source);
        var right = workingSet(target);
        return WorkingSetLineagePolicy.comparableBindings(left, right).stream().flatMap(binding ->
                trackers.findTracker(binding.trackerId()).stream().filter(tracker -> tracker.sourceId() != null)
                .filter(tracker -> parentIncludes(left, right, tracker.sourceId()))
                .map(tracker -> tracker.workingSetId().equals(source)
                        ? new TrackerPair(tracker.trackerId(), tracker.sourceId())
                        : new TrackerPair(tracker.sourceId(), tracker.trackerId()))).toList();
    }

    private boolean parentIncludes(WorkingSet source, WorkingSet target, TrackerId trackerId) {
        WorkingSet parent = source.id().equals(target.parentWorkingSetId()) ? source : target;
        return trackers.findTracker(trackerId)
                .filter(tracker -> parent.id().equals(tracker.workingSetId()))
                .filter(tracker -> WorkingSet.DEFAULT_ID.equals(parent.id()) || parent.hasTrackerBinding(trackerId))
                .isPresent();
    }

    Difference current(Difference original, Caller caller) {
        var pair = original.trackers();
        var family = original.family();
        Side source = read(familyIds(pair.source()).get(family), caller);
        Side target = read(familyIds(pair.target()).get(family), caller);
        return new Difference(original.key(), family, pair, source, target, differingFields(source, target));
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
                return new Side(id, contentId, revision, contentRevision, stamp.version(), stamp.lockVersion(),
                        Presence.DELETED, null, Map.of());
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

    void assertWriteCapability(Difference difference, boolean create, Caller caller) {
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
