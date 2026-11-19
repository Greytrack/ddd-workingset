package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSet.TrackerBinding;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.error.WorkingsetResultCode;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作集绑定解除与聚合删除：默认根保护、子工作集保护、成员可解除性预检与解除顺序。
 *
 * <p>删除顺序为 TrackerBinding → SpaceBinding → WorkingSet；能力范围只到 Working Set，
 * 业务对象生命周期由各 BC 负责（D7/D16）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingSetDeletionService {
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;
    private final LightTrackerItemQueryProvider itemQueries;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final BusinessTrackerItems businessItems;

    private static void rejectIndependentFolderRemoval(Tracker tracker) {
        if (tracker.isRootFolder()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "根文件夹须随产品空间卸载");
        }
    }

    @Transactional
    public void unbindTracker(WorkingSetId id, TrackerId trackerId) {
        WorkingSet workingSet = lockWorkingSet(id);
        TrackerBinding binding = workingSet.trackerBinding(trackerId);
        rejectIndependentFolderRemoval(lockTracker(trackerId));
        checkUnbind(binding);
        workingSet.unbindTracker(trackerId);
        workingSets.save(workingSet);
        deleteOwnedTracker(binding);
    }

    @Transactional
    public void removeSpace(WorkingSetId id, String spaceId) {
        removeSpace(lockWorkingSet(id), spaceId);
    }

    @Transactional
    public void deleteWorkingSet(WorkingSetId id) {
        WorkingSet.requireDeletable(id);
        WorkingSet workingSet = lockWorkingSet(id);
        assertNoChildren(id);
        List<TrackerBinding> bindings = workingSet.trackers().stream()
                .sorted(Comparator.comparing(binding -> binding.trackerId().value()))
                .toList();
        Map<String, String> blockers = new LinkedHashMap<>();
        for (TrackerBinding binding : bindings) {
            try {
                checkUnbind(binding);
            } catch (BindingFailure failure) {
                blockers.put(binding.trackerId().value(), failure.reason().name());
            }
        }
        if (!blockers.isEmpty()) {
            throw new BindingFailure(Reason.MemberCannotBeUnbound, id.value(), Map.of("members", blockers));
        }
        bindings.forEach(binding -> workingSet.unbindTracker(binding.trackerId()));
        workingSet.spaces().forEach(binding -> workingSet.removeSpace(binding.productSpaceId()));
        workingSets.save(workingSet);
        bindings.forEach(this::deleteOwnedTracker);
        workingSets.delete(workingSet);
    }

    private void assertNoChildren(WorkingSetId id) {
        List<String> children = queries.findChildren(id.numericValue()).stream().map(WorkingSetId::of).map(WorkingSetId::value).toList();
        if (!children.isEmpty()) {
            throw new BindingFailure(Reason.WorkingSetHasChildren, id.value(), Map.of("childWorkingSetIds", children));
        }
    }

    private void checkUnbind(TrackerBinding binding) {
        Tracker tracker = lockTracker(binding.trackerId());
        if (binding.sharingMode() == SharingMode.UNSHARED) {
            requireDeletable(tracker);
        }
    }

    private void deleteOwnedTracker(TrackerBinding binding) {
        if (binding.sharingMode() == SharingMode.UNSHARED) {
            trackers.deleteTracker(binding.trackerId());
        }
    }

    private void removeSpace(WorkingSet workingSet, String spaceId) {
        if (!workingSet.hasSpaceBinding(spaceId)) {
            throw new BindingFailure(Reason.SpaceBindingNotFound, spaceId);
        }
        List<TrackerBinding> members = workingSet.trackers().stream()
                .filter(binding -> binding.productSpaceId().equals(spaceId)).toList();
        for (TrackerBinding binding : members) {
            if (!lockTracker(binding.trackerId()).isRootFolder()) {
                throw new BindingFailure(Reason.SpaceStillHasTrackers, spaceId);
            }
        }
        members.forEach(this::checkUnbind);
        members.forEach(binding -> workingSet.unbindTracker(binding.trackerId()));
        workingSet.removeSpace(spaceId);
        workingSets.save(workingSet);
        members.forEach(this::deleteOwnedTracker);
    }

    private void requireDeletable(Tracker tracker) {
        TrackerId id = tracker.trackerId();
        tracker.requireDeletable(itemQueries.existsInTracker(id) || businessItems.existsInTracker(id),
                trackerQueries.hasOrgChildren(id), trackerQueries.hasDerivedChildren(id));
    }

    private WorkingSet lockWorkingSet(WorkingSetId id) {
        return workingSets.findByIdForUpdate(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    private Tracker lockTracker(TrackerId id) {
        return trackers.lockTracker(id)
                .orElseThrow(() -> new BindingFailure(Reason.TrackerNotFound, id.value()));
    }
}
