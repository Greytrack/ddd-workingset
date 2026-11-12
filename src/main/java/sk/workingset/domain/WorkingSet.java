package sk.workingset.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;

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
    private final Map<String, SpaceBinding> spaces = new LinkedHashMap<>();
    private final Map<TrackerId, TrackerBinding> trackers = new LinkedHashMap<>();

    public WorkingSet(WorkingSetId id, Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this.id = Objects.requireNonNull(id, "id");
        spaces.forEach(binding -> this.spaces.put(binding.productSpaceId(), binding));
        trackers.forEach(binding -> this.trackers.put(binding.trackerId(), binding));
    }

    public WorkingSetId id() { return id; }
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

    public void requireDirectUpstream(Tracker source) {
        if (DEFAULT_ID.equals(id)) {
            throw new BindingFailure(Reason.WorkingSetNotWritable, "default Working Set cannot bind upstream Trackers");
        }
        // S5-A 的纳入范围为已登记的首层工作集，直接上游固定为默认根。
        if (!DEFAULT_ID.equals(source.workingSetId()) || source.isDerived()) {
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
