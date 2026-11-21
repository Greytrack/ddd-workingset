package sk.workingset.application.query.provider;

import org.springframework.stereotype.Service;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.TrackerItemContent;
import sk.workingset.application.port.BusinessObjectTypeAdapter;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetLineageDomainService;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.MergeResCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Compare/Merge 共用的只读证据读取：按 Tracker 路由到业务对象类型，不触达任何写仓储。
 *
 * <p>合并登记时复用同一套只读能力校验证据。
 */
@Service
public class MergeEvidenceReader {

    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemQueryProvider itemQueries;
    private final BusinessObjectTypeRegistry registry;

    public MergeEvidenceReader(WorkingSetRepository workingSets, TrackerRepository trackers,
                               LightTrackerItemQueryProvider itemQueries, BusinessObjectTypeRegistry registry) {
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.itemQueries = itemQueries;
        this.registry = registry;
    }

    private static void putFamily(Map<CommonId, LocalId> families, CommonId family, LocalId id) {
        LocalId previous = families.putIfAbsent(family, id);
        if (previous != null && !previous.equals(id)) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "multiple variants in family " + family.value());
        }
    }

    private static Side unreadable(LocalId id, FailureReason reason) {
        return new Side(id, null, null, null, null, null, Presence.UNREADABLE, reason.name(), Map.of());
    }

    public static List<String> differingFields(Side source, Side target) {
        if (source.presence() != Presence.PRESENT || target.presence() != Presence.PRESENT) {
            return List.of();
        }
        TreeSet<String> keys = new TreeSet<>(source.fields().keySet());
        keys.addAll(target.fields().keySet());
        return keys.stream().filter(key -> !Objects.equals(source.fields().get(key), target.fields().get(key)))
                .toList();
    }

    /**
     * 可比较的直属父子 Tracker 对；不可比较或未注册的类型不进入候选。
     */
    public List<TrackerPair> candidates(WorkingSetId source, WorkingSetId target) {
        WorkingSet left = workingSet(source);
        WorkingSet right = workingSet(target);
        WorkingSet child = WorkingSetLineageDomainService.childOf(left, right);
        return WorkingSetLineageDomainService.comparableBindings(left, right).stream().flatMap(binding ->
                trackers.findById(binding.trackerId()).stream()
                        // The binding must belong to the direct child Working Set; this rejects cross-level
                        // and Shared bindings that happen to carry a sourceId.
                        .filter(tracker -> tracker.workingSetId().equals(child.id()))
                        .filter(tracker -> tracker.sourceId() != null)
                        .filter(tracker -> comparable(tracker.trackerId()))
                        .filter(tracker -> parentIncludes(left, right, tracker))
                        .map(tracker -> tracker.workingSetId().equals(source)
                                ? new TrackerPair(source, tracker.trackerId(), target, tracker.sourceId())
                                : new TrackerPair(source, tracker.sourceId(), target, tracker.trackerId()))).toList();
    }

    public Difference current(Difference original) {
        TrackerPair pair = original.trackers();
        CommonId family = original.family();
        Side source = read(familyIds(pair.source()).get(family));
        Side target = read(familyIds(pair.target()).get(family));
        return new Difference(family, pair, source, target, differingFields(source, target));
    }

    public Map<CommonId, LocalId> familyIds(TrackerId tracker) {
        Map<CommonId, LocalId> families = new LinkedHashMap<>();
        BusinessObjectTypeAdapter adapter = registry.forTracker(tracker);
        adapter.catalog().identitiesForMerge(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        itemQueries.findInTracker(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        return families;
    }

    public Side read(LocalId id) {
        if (id == null) {
            return Side.absent();
        }
        LightTrackerItemSummary variant = itemQueries.findById(id).orElse(null);
        LocalId contentId = variant == null ? id : variant.effectiveContentSource();
        boolean inherited = variant != null && !variant.materialized();
        TrackerItemQueryProvider port;
        try {
            port = registry.forItem(contentId).query(contentId);
        } catch (DomainException failure) {
            if (FailureReason.of(failure) == FailureReason.NOT_FOUND) {
                return unreadable(id, FailureReason.NOT_FOUND);
            }
            throw failure;
        }
        try {
            TrackerItemQueryProvider.ContentEvidence stamp = port.evidence();
            Revision revision = variant == null || !inherited ? stamp.revision() : variant.revision();
            Revision contentRevision = inherited ? variant.effectiveContentRevision() : stamp.revision();
            if (!inherited && stamp.deleted()) {
                return new Side(id, contentId, revision, contentRevision, stamp.version(), stamp.lockVersion(),
                        Presence.DELETED, null, Map.of());
            }
            TrackerItemContent content = inherited ? port.readInheritedAt(contentRevision) : port.readAt(contentRevision);
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

    private boolean comparable(TrackerId trackerId) {
        try {
            return registry.forTracker(trackerId).capabilities().comparable();
        } catch (DomainException failure) {
            if (FailureReason.of(failure) == FailureReason.CAPABILITY_NOT_SUPPORTED) {
                return false;
            }
            throw failure;
        }
    }

    private boolean parentIncludes(WorkingSet source, WorkingSet target, Tracker child) {
        if (child.sourceId() == null) {
            return false;
        }
        WorkingSet parent = source.id().equals(target.parentWorkingSetId()) ? source : target;
        return trackers.findById(child.sourceId())
                .filter(sourceTracker -> parent.id().equals(sourceTracker.workingSetId()))
                .filter(sourceTracker -> WorkingSetLineageDomainService.isDirectDerivation(child, sourceTracker))
                .filter(sourceTracker -> WorkingSet.DEFAULT_ID.equals(parent.id())
                        || parent.hasTrackerBinding(sourceTracker.trackerId()))
                .isPresent();
    }

    private WorkingSet workingSet(WorkingSetId id) {
        return workingSets.findById(id).orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, id.value()));
    }
}
