package sk.workingset.domain;

import java.util.Map;

public final class BindingFailure extends RuntimeException {
    public enum Reason {
        WorkingSetNotFound, WorkingSetNotWritable, ProductSpaceNotFound, AlreadyBound,
        MissingSpaceBinding, TrackerAlreadyBound, SlotOccupied, SourceTrackerNotFound,
        SharingModeMismatch, TrackerOutsideSlot, SourceItemNotFound, SourceItemMismatch,
        TrackerNotFound, TrackerBindingNotFound,
        SpaceBindingNotFound, SpaceStillHasTrackers, SpaceStillBoundInWorkingSet,
        TrackerHasItems, TrackerHasOrgChildren, TrackerHasDerivedChildren,
        DefaultWorkingSetUndeletable, MemberCannotBeUnbound
    }

    private final Reason reason;
    private final Map<String, Object> context;

    public BindingFailure(Reason reason, String detail) {
        this(reason, detail, Map.of());
    }

    public BindingFailure(Reason reason, String detail, Map<String, Object> context) {
        super(detail);
        this.reason = reason;
        this.context = Map.copyOf(context);
    }

    public Map<String, Object> context() {
        return context;
    }

    public Reason reason() {
        return reason;
    }
}
