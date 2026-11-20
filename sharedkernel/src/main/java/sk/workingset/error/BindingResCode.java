package sk.workingset.error;

import sk.common.error.DomainErrorType;
import sk.common.error.DomainResCode;

/** T4 binding and deletion codes with their public reason values. */
public enum BindingResCode implements DomainResCode {
    WORKING_SET_NOT_EXISTS(DomainErrorType.NOT_FOUND, 1201, "Working Set does not exist", "WorkingSetNotFound"),
    WORKING_SET_NOT_WRITABLE(DomainErrorType.CONFLICT, 1202, "Working set not writable", "WorkingSetNotWritable"),
    WORKING_SET_CYCLE(DomainErrorType.CONFLICT, 1203, "Working set cycle", "WorkingSetCycle"),
    WORKING_SET_ALREADY_EXISTS(DomainErrorType.ALREADY_EXISTS, 1204, "Working set already exists", "WorkingSetAlreadyExists"),
    WORKING_SET_PARENT_IMMUTABLE(DomainErrorType.CONFLICT, 1205, "Working set parent immutable", "WorkingSetParentImmutable"),
    WORKING_SETS_NOT_ADJACENT(DomainErrorType.CONFLICT, 1206, "Working sets not adjacent", "WorkingSetsNotAdjacent"),
    WORKING_SET_HAS_CHILDREN(DomainErrorType.CONFLICT, 1207, "Working set has children", "WorkingSetHasChildren"),
    PRODUCT_SPACE_NOT_FOUND(DomainErrorType.NOT_FOUND, 1208, "Product space not found", "ProductSpaceNotFound"),
    ALREADY_BOUND(DomainErrorType.CONFLICT, 1209, "Already bound", "AlreadyBound"),
    MISSING_SPACE_BINDING(DomainErrorType.CONFLICT, 1210, "Missing space binding", "MissingSpaceBinding"),
    TRACKER_ALREADY_BOUND(DomainErrorType.CONFLICT, 1211, "Tracker already bound", "TrackerAlreadyBound"),
    SLOT_OCCUPIED(DomainErrorType.CONFLICT, 1212, "Slot occupied", "SLOT_OCCUPIED"),
    SOURCE_TRACKER_NOT_FOUND(DomainErrorType.NOT_FOUND, 1213, "Source tracker not found", "SourceTrackerNotFound"),
    SHARING_MODE_MISMATCH(DomainErrorType.UNPROCESSABLE_ENTITY, 1214, "Sharing mode mismatch", "SharingModeMismatch"),
    TRACKER_OUTSIDE_SLOT(DomainErrorType.UNPROCESSABLE_ENTITY, 1215, "Tracker outside slot", "TrackerOutsideSlot"),
    SOURCE_ITEM_NOT_FOUND(DomainErrorType.NOT_FOUND, 1216, "Source item not found", "SourceItemNotFound"),
    SOURCE_ITEM_MISMATCH(DomainErrorType.UNPROCESSABLE_ENTITY, 1217, "Source item mismatch", "SourceItemMismatch"),
    TRACKER_NOT_FOUND(DomainErrorType.NOT_FOUND, 1218, "Tracker not found", "TrackerNotFound"),
    TRACKER_BINDING_NOT_FOUND(DomainErrorType.NOT_FOUND, 1219, "Tracker binding not found", "TrackerBindingNotFound"),
    SPACE_BINDING_NOT_FOUND(DomainErrorType.NOT_FOUND, 1220, "Space binding not found", "SpaceBindingNotFound"),
    SPACE_STILL_HAS_TRACKERS(DomainErrorType.CONFLICT, 1221, "Space still has trackers", "SpaceStillHasTrackers"),
    SPACE_STILL_BOUND_IN_WORKING_SET(DomainErrorType.CONFLICT, 1222, "Space still bound in working set", "SpaceStillBoundInWorkingSet"),
    TRACKER_HAS_ITEMS(DomainErrorType.CONFLICT, 1223, "Tracker has items", "TrackerHasItems"),
    TRACKER_HAS_ORG_CHILDREN(DomainErrorType.CONFLICT, 1224, "Tracker has org children", "TrackerHasOrgChildren"),
    TRACKER_HAS_DERIVED_CHILDREN(DomainErrorType.CONFLICT, 1225, "Tracker has derived children", "TrackerHasDerivedChildren"),
    DEFAULT_WORKING_SET_UNDELETABLE(DomainErrorType.CONFLICT, 1226, "Default working set undeletable", "DefaultWorkingSetUndeletable"),
    MEMBER_CANNOT_BE_UNBOUND(DomainErrorType.CONFLICT, 1227, "Member cannot be unbound", "MemberCannotBeUnbound"),
    NO_DERIVABLE_TRACKER(DomainErrorType.CONFLICT, 1228, "No derivable tracker", "NoDerivableTracker");

    private final DomainErrorType type;
    private final int code;
    private final String desc;
    private final String reason;

    BindingResCode(DomainErrorType type, int code, String desc, String reason) {
        this.type = type;
        this.code = code;
        this.desc = desc;
        this.reason = reason;
    }

    @Override
    public DomainErrorType getType() { return type; }

    @Override
    public int getCode() { return code; }

    @Override
    public String getDesc() { return desc; }

    public String reason() { return reason; }
}
