package sk.workingset.domain;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

import java.util.Map;

/**
 * 绑定/删除失败：携带原因与可观察详情（子级、成员、空间等），由 Web 层映射为 HTTP 状态。
 */
public final class BindingFailure extends DomainException {
    private final Reason reason;
    private final Map<String, Object> context;

    public BindingFailure(Reason reason, String detail) {
        this(reason, detail, Map.of());
    }

    public BindingFailure(Reason reason, String detail, Map<String, Object> context) {
        super(reason.resultCode(), detail, null);
        this.reason = reason;
        this.context = Map.copyOf(context);
    }

    public Map<String, Object> context() {
        return context;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        WorkingSetNotFound(WorkingsetResultCode.WORKING_SET_NOT_EXISTS),
        WorkingSetNotWritable(WorkingsetResultCode.WORKING_SET_NOT_WRITABLE),
        WorkingSetCycle(WorkingsetResultCode.WORKING_SET_CYCLE),
        WorkingSetAlreadyExists(WorkingsetResultCode.WORKING_SET_ALREADY_EXISTS),
        WorkingSetParentImmutable(WorkingsetResultCode.WORKING_SET_PARENT_IMMUTABLE),
        WorkingSetsNotAdjacent(WorkingsetResultCode.WORKING_SETS_NOT_ADJACENT),
        WorkingSetHasChildren(WorkingsetResultCode.WORKING_SET_HAS_CHILDREN),
        ProductSpaceNotFound(WorkingsetResultCode.PRODUCT_SPACE_NOT_FOUND),
        AlreadyBound(WorkingsetResultCode.ALREADY_BOUND),
        MissingSpaceBinding(WorkingsetResultCode.MISSING_SPACE_BINDING),
        TrackerAlreadyBound(WorkingsetResultCode.TRACKER_ALREADY_BOUND),
        SourceTrackerNotFound(WorkingsetResultCode.SOURCE_TRACKER_NOT_FOUND),
        SharingModeMismatch(WorkingsetResultCode.SHARING_MODE_MISMATCH),
        TrackerOutsideSlot(WorkingsetResultCode.TRACKER_OUTSIDE_SLOT),
        SourceItemNotFound(WorkingsetResultCode.SOURCE_ITEM_NOT_FOUND),
        SourceItemMismatch(WorkingsetResultCode.SOURCE_ITEM_MISMATCH),
        TrackerNotFound(WorkingsetResultCode.TRACKER_NOT_FOUND),
        TrackerBindingNotFound(WorkingsetResultCode.TRACKER_BINDING_NOT_FOUND),
        SpaceBindingNotFound(WorkingsetResultCode.SPACE_BINDING_NOT_FOUND),
        SpaceStillHasTrackers(WorkingsetResultCode.SPACE_STILL_HAS_TRACKERS),
        SpaceStillBoundInWorkingSet(WorkingsetResultCode.SPACE_STILL_BOUND_IN_WORKING_SET),
        TrackerHasItems(WorkingsetResultCode.TRACKER_HAS_ITEMS),
        TrackerHasOrgChildren(WorkingsetResultCode.TRACKER_HAS_ORG_CHILDREN),
        TrackerHasDerivedChildren(WorkingsetResultCode.TRACKER_HAS_DERIVED_CHILDREN),
        DefaultWorkingSetUndeletable(WorkingsetResultCode.DEFAULT_WORKING_SET_UNDELETABLE),
        MemberCannotBeUnbound(WorkingsetResultCode.MEMBER_CANNOT_BE_UNBOUND),
        NoDerivableTracker(WorkingsetResultCode.NO_DERIVABLE_TRACKER);

        private final WorkingsetResultCode resultCode;

        Reason(WorkingsetResultCode resultCode) {
            this.resultCode = resultCode;
        }

        public WorkingsetResultCode resultCode() {
            return resultCode;
        }
    }
}
