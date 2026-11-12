package sk.workingset.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.ProductSpaceRepository;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSet.TrackerBinding;
import sk.workingset.domain.WorkingSetId;

public class WorkingSetDeletionService {
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final LightTrackerItemRepository items;
    private final BusinessTrackerItems businessItems;

    public WorkingSetDeletionService(WorkingSetRepository workingSets, TrackerRepository trackers,
                                     ProductSpaceRepository spaces, LightTrackerItemRepository items,
                                     BusinessTrackerItems businessItems) {
        this.workingSets = workingSets;
        this.trackers = trackers;
        this.spaces = spaces;
        this.items = items;
        this.businessItems = businessItems;
    }

    @Transactional
    public void unbindTracker(WorkingSetId id, TrackerId trackerId) {
        var workingSet = lockWorkingSet(id);
        var binding = workingSet.trackerBinding(trackerId);
        checkUnbind(binding);
        unbind(workingSet, binding);
    }

    @Transactional
    public void removeSpace(WorkingSetId id, String spaceId) {
        removeSpace(lockWorkingSet(id), spaceId);
    }

    @Transactional
    public void deleteTracker(TrackerId id) {
        requireDeletable(lockTracker(id));
        workingSets.deleteBindingsToTracker(id);
        trackers.deleteTracker(id);
    }

    @Transactional
    public void deleteProductSpace(String id) {
        spaces.lockProductSpace(id).orElseThrow(() -> new BindingFailure(Reason.ProductSpaceNotFound, id));
        List<String> bound = workingSets.findWorkingSetsBoundToSpace(id).stream().map(WorkingSetId::value).toList();
        if (!bound.isEmpty()) {
            throw new BindingFailure(Reason.SpaceStillBoundInWorkingSet, id, Map.of("workingSetIds", bound));
        }
        if (trackers.hasTrackersInSpace(id)) {
            throw new BindingFailure(Reason.SpaceStillHasTrackers, id);
        }
        spaces.deleteProductSpace(id);
    }

    @Transactional
    public void deleteWorkingSet(WorkingSetId id) {
        WorkingSet.requireDeletable(id);
        var workingSet = lockWorkingSet(id);
        assertNoChildren(id);
        var bindings = workingSet.trackers().stream()
                .sorted(Comparator.comparing(binding -> binding.trackerId().value()))
                .toList();
        Map<String, String> blockers = new LinkedHashMap<>();
        for (var binding : bindings) {
            try {
                checkUnbind(binding);
            } catch (BindingFailure failure) {
                blockers.put(binding.trackerId().value(), failure.reason().name());
            }
        }
        if (!blockers.isEmpty()) {
            throw new BindingFailure(Reason.MemberCannotBeUnbound, id.value(), Map.of("members", blockers));
        }
        bindings.forEach(binding -> unbind(workingSet, binding));
        workingSet.spaces().forEach(binding -> removeSpace(workingSet, binding.productSpaceId()));
        workingSets.deleteWorkingSet(id);
    }

    private void assertNoChildren(WorkingSetId id) {
        var children = workingSets.findChildren(id).stream().map(WorkingSetId::value).toList();
        if (!children.isEmpty()) {
            throw new BindingFailure(Reason.WorkingSetHasChildren, id.value(), Map.of("childWorkingSetIds", children));
        }
    }

    private void checkUnbind(TrackerBinding binding) {
        var tracker = lockTracker(binding.trackerId());
        if (binding.sharingMode() == SharingMode.UNSHARED) {
            requireDeletable(tracker);
        }
    }

    private void unbind(WorkingSet workingSet, TrackerBinding binding) {
        workingSet.unbindTracker(binding.trackerId());
        workingSets.deleteTrackerBinding(workingSet.id(), binding.trackerId());
        if (binding.sharingMode() == SharingMode.UNSHARED) {
            trackers.deleteTracker(binding.trackerId());
        }
    }

    private void removeSpace(WorkingSet workingSet, String spaceId) {
        workingSet.removeSpace(spaceId);
        workingSets.deleteSpaceBinding(workingSet.id(), spaceId);
    }

    private void requireDeletable(Tracker tracker) {
        var id = tracker.trackerId();
        tracker.requireDeletable(items.existsInTracker(id) || businessItems.existsInTracker(id),
                trackers.hasOrgChildren(id), trackers.hasDerivedChildren(id));
    }

    private WorkingSet lockWorkingSet(WorkingSetId id) {
        return workingSets.lockWorkingSet(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    private Tracker lockTracker(TrackerId id) {
        return trackers.lockTracker(id)
                .orElseThrow(() -> new BindingFailure(Reason.TrackerNotFound, id.value()));
    }
}
