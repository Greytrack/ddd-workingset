package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.application.TrackerItemContent;
import com.airedgesoft.sharekernel.workingset.application.port.BusinessObjectTypeAdapter;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.Difference;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.Presence;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.Side;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.TrackerPair;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetLineageDomainService;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharekernel.workingset.error.MergeResCode;
import org.springframework.stereotype.Service;

import java.util.*;

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

    private static void putFamily(Map<Long, Long> families, Long family, Long id) {
        Long previous = families.putIfAbsent(family, id);
        if (previous != null && !previous.equals(id)) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "multiple variants in family " + Long.toString(family));
        }
    }

    private static Side unreadable(Long id, FailureReason reason) {
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
    public List<TrackerPair> candidates(Long source, Long target) {
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
        Long family = original.family();
        Side source = read(familyIds(pair.source()).get(family));
        Side target = read(familyIds(pair.target()).get(family));
        return new Difference(family, pair, source, target, differingFields(source, target));
    }

    public Map<Long, Long> familyIds(Long tracker) {
        Map<Long, Long> families = new LinkedHashMap<>();
        BusinessObjectTypeAdapter adapter = registry.forTracker(tracker);
        adapter.catalog().identitiesForMerge(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        itemQueries.findInTracker(tracker).forEach(item -> putFamily(families, item.commonId(), item.localId()));
        return families;
    }

    public Side read(Long id) {
        if (id == null) {
            return Side.absent();
        }
        LightTrackerItemSummary variant = itemQueries.findById(id).orElse(null);
        Long contentId = variant == null ? id : variant.effectiveContentSource();
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
            Integer revision = variant == null || !inherited ? stamp.revision() : variant.revision();
            Integer contentRevision = inherited ? variant.effectiveContentRevision() : stamp.revision();
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

    private boolean comparable(Long trackerId) {
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

    private WorkingSet workingSet(Long id) {
        return workingSets.findById(id).orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, Long.toString(id)));
    }
}
