package sk.workingset.domain;

import java.util.Map;

/** 绑定/删除失败：携带原因与可观察详情（子级、成员、空间等），由 Web 层映射为 HTTP 状态。 */
public final class BindingFailure extends RuntimeException {
    public enum Reason {
        WorkingSetNotFound, WorkingSetNotWritable, WorkingSetCycle, WorkingSetAlreadyExists,
        WorkingSetParentImmutable, WorkingSetsNotAdjacent, WorkingSetHasChildren,
        ProductSpaceNotFound, AlreadyBound,
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
