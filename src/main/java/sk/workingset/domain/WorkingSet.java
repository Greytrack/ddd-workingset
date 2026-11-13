package sk.workingset.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;

/**
 * WorkingSet 聚合根：默认根不可删除、单父谱系（{@code parentWorkingSetId}）、
 * TrackerBinding 与 SpaceBinding 的归属与解除规则。
 */
public final class WorkingSet {

    public static final WorkingSetId DEFAULT_ID = WorkingSetId.of("1");

    public enum SpaceOrigin { INITIAL_PROJECTION, EXPLICIT }
    public enum SpaceState { ACTIVE }
    public enum TrackerOrigin { SHARED_REUSE, UNSHARED_DERIVATION }

    public record SpaceBinding(WorkingSetId workingSetId, String productSpaceId,
                               SpaceOrigin origin, SpaceState state) {}

    public record TrackerBinding(WorkingSetId workingSetId, TrackerId trackerId, String productSpaceId,
                                 String classificationId, SharingMode sharingMode, TrackerOrigin origin) {}

    private final WorkingSetId id;
    private final WorkingSetId parentWorkingSetId;
    private final Map<String, SpaceBinding> spaces = new LinkedHashMap<>();
    private final Map<TrackerId, TrackerBinding> trackers = new LinkedHashMap<>();

    public WorkingSet(WorkingSetId id, WorkingSetId parentWorkingSetId,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.equals(parentWorkingSetId)) {
            throw new BindingFailure(Reason.WorkingSetCycle, id.value());
        }
        if (DEFAULT_ID.equals(id) != (parentWorkingSetId == null)) {
            throw new IllegalArgumentException("only the default Working Set has no parent");
        }
        this.parentWorkingSetId = parentWorkingSetId;
        spaces.forEach(binding -> this.spaces.put(binding.productSpaceId(), binding));
        trackers.forEach(binding -> this.trackers.put(binding.trackerId(), binding));
    }

    public static WorkingSet child(WorkingSetId id, WorkingSetId parentId) {
        return new WorkingSet(id, Objects.requireNonNull(parentId, "parentId"), List.of(), List.of());
    }

    public WorkingSetId id() { return id; }
    public WorkingSetId parentWorkingSetId() { return parentWorkingSetId; }
    public List<SpaceBinding> spaces() { return List.copyOf(spaces.values()); }
    public List<TrackerBinding> trackers() { return List.copyOf(trackers.values()); }

    public SpaceBinding bindSpace(ProductSpace space) {
        var binding = new SpaceBinding(id, space.id(), SpaceOrigin.EXPLICIT, SpaceState.ACTIVE);
        if (spaces.putIfAbsent(space.id(), binding) != null) {
            throw new BindingFailure(Reason.AlreadyBound, space.id());
        }
        return binding;
    }

    public TrackerBinding trackerBinding(TrackerId trackerId) {
        var binding = trackers.get(trackerId);
        if (binding == null) {
            throw new BindingFailure(Reason.TrackerBindingNotFound, trackerId.value());
        }
        return binding;
    }

    public void unbindTracker(TrackerId trackerId) {
        trackerBinding(trackerId);
        trackers.remove(trackerId);
    }

    public void removeSpace(String spaceId) {
        if (trackers.values().stream().anyMatch(binding -> binding.productSpaceId().equals(spaceId))) {
            throw new BindingFailure(Reason.SpaceStillHasTrackers, spaceId);
        }
        if (spaces.remove(spaceId) == null) {
            throw new BindingFailure(Reason.SpaceBindingNotFound, spaceId);
        }
    }

    public static void requireDeletable(WorkingSetId id) {
        if (DEFAULT_ID.equals(id)) {
            throw new BindingFailure(Reason.DefaultWorkingSetUndeletable, id.value());
        }
    }

    public WorkingSetId requireParentId() {
        if (parentWorkingSetId == null) {
            throw new BindingFailure(Reason.WorkingSetNotWritable, "default Working Set cannot bind upstream Trackers");
        }
        return parentWorkingSetId;
    }

    public boolean hasTrackerBinding(TrackerId trackerId) {
        return trackers.containsKey(trackerId);
    }

    public void requireDirectParent(Tracker source, boolean boundInParent) {
        WorkingSetId parentId = requireParentId();
        boolean ownedByParent = parentId.equals(source.workingSetId());
        // 默认根的原生 Tracker 直接来自平台投影。子工作集的有效范围由绑定定义。
        boolean inParentRange = DEFAULT_ID.equals(parentId) ? ownedByParent : boundInParent;
        if (!inParentRange || (source.sharingMode() == SharingMode.UNSHARED && !ownedByParent)) {
            throw new BindingFailure(Reason.SourceTrackerNotFound, source.trackerId().value());
        }
    }

    public TrackerBinding bindTracker(Tracker tracker, Classification classification, SharingMode requestedMode) {
        if (!spaces.containsKey(tracker.productSpaceId())) {
            throw new BindingFailure(Reason.MissingSpaceBinding, tracker.productSpaceId());
        }
        if (!tracker.classificationId().equals(classification.id())
                || tracker.sharingMode() != requestedMode || classification.sharingMode() != requestedMode) {
            throw new BindingFailure(Reason.SharingModeMismatch, classification.id());
        }
        if (trackers.containsKey(tracker.trackerId())) {
            throw new BindingFailure(Reason.TrackerAlreadyBound, tracker.trackerId().value());
        }
        if (trackers.values().stream().anyMatch(existing -> existing.productSpaceId().equals(tracker.productSpaceId())
                && existing.classificationId().equals(classification.id()))) {
            throw new BindingFailure(Reason.SlotOccupied, tracker.productSpaceId() + "/" + classification.id());
        }
        var origin = requestedMode == SharingMode.SHARED ? TrackerOrigin.SHARED_REUSE : TrackerOrigin.UNSHARED_DERIVATION;
        var binding = new TrackerBinding(id, tracker.trackerId(), tracker.productSpaceId(), classification.id(),
                requestedMode, origin);
        trackers.put(binding.trackerId(), binding);
        return binding;
    }
}
