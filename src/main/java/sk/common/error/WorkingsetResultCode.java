package sk.common.error;

/**
 * Public API error codes (T4 §5.4, API contract v1).
 *
 * <p>Codes are stable and append-only: never renumber, reuse, or change the meaning of an
 * existing code. {@code reason} is the stable category; {@code message} is not stable.
 * Ranges: 1000–1099 common, 1100–1199 T4 nine categories, 1200–1299 working set/binding,
 * 1300–1399 merge, 1400–1499 materialization/lifecycle lock. New codes must be added in
 * the matching range and registered in the contract before being exposed.
 */
public enum WorkingsetResultCode implements ResCode {
    APPLICATION_NOT_FOUND(1101, "Not found"),
    APPLICATION_NOT_INCLUDED(1102, "Not included"),
    APPLICATION_DENIED(1103, "Denied"),
    APPLICATION_DELETED(1104, "Deleted"),
    APPLICATION_HISTORY_UNAVAILABLE(1105, "History unavailable"),
    APPLICATION_PREPARING(1106, "Preparing"),
    APPLICATION_CAPABILITY_NOT_SUPPORTED(1107, "Capability not supported"),
    APPLICATION_DEPENDENCY_UNAVAILABLE(1108, "Dependency unavailable"),
    APPLICATION_UNKNOWN(1109, "Unknown"),
    WORKING_SET_NOT_EXISTS(1201, "Working Set does not exist"),
    WORKING_SET_NOT_WRITABLE(1202, "Working set not writable"),
    WORKING_SET_CYCLE(1203, "Working set cycle"),
    WORKING_SET_ALREADY_EXISTS(1204, "Working set already exists"),
    WORKING_SET_PARENT_IMMUTABLE(1205, "Working set parent immutable"),
    WORKING_SETS_NOT_ADJACENT(1206, "Working sets not adjacent"),
    WORKING_SET_HAS_CHILDREN(1207, "Working set has children"),
    PRODUCT_SPACE_NOT_FOUND(1208, "Product space not found"),
    ALREADY_BOUND(1209, "Already bound"),
    MISSING_SPACE_BINDING(1210, "Missing space binding"),
    TRACKER_ALREADY_BOUND(1211, "Tracker already bound"),
    SLOT_OCCUPIED(1212, "Slot occupied"),
    SOURCE_TRACKER_NOT_FOUND(1213, "Source tracker not found"),
    SHARING_MODE_MISMATCH(1214, "Sharing mode mismatch"),
    TRACKER_OUTSIDE_SLOT(1215, "Tracker outside slot"),
    SOURCE_ITEM_NOT_FOUND(1216, "Source item not found"),
    SOURCE_ITEM_MISMATCH(1217, "Source item mismatch"),
    TRACKER_NOT_FOUND(1218, "Tracker not found"),
    TRACKER_BINDING_NOT_FOUND(1219, "Tracker binding not found"),
    SPACE_BINDING_NOT_FOUND(1220, "Space binding not found"),
    SPACE_STILL_HAS_TRACKERS(1221, "Space still has trackers"),
    SPACE_STILL_BOUND_IN_WORKING_SET(1222, "Space still bound in working set"),
    TRACKER_HAS_ITEMS(1223, "Tracker has items"),
    TRACKER_HAS_ORG_CHILDREN(1224, "Tracker has org children"),
    TRACKER_HAS_DERIVED_CHILDREN(1225, "Tracker has derived children"),
    DEFAULT_WORKING_SET_UNDELETABLE(1226, "Default working set undeletable"),
    MEMBER_CANNOT_BE_UNBOUND(1227, "Member cannot be unbound"),
    NO_DERIVABLE_TRACKER(1228, "No derivable tracker"),
    MERGE_MERGE_NOT_ALLOWED(1301, "Merge not allowed"),
    MERGE_ROUND_NOT_FOUND(1302, "Round not found"),
    MERGE_ROUND_CLOSED(1303, "Round closed"),
    MERGE_ROUND_IN_PROGRESS(1304, "Round in progress"),
    MERGE_PAIR_MERGE_IN_PROGRESS(1305, "Pair merge in progress"),
    MERGE_STALE_EVIDENCE(1306, "Stale evidence"),
    MERGE_INVALID_DECISION(1307, "Invalid decision"),
    MERGE_REJECTED(1308, "Rejected"),
    MERGE_CONFLICT(1309, "Conflict"),
    MATERIALIZATION_REJECTED(1401, "Materialization rejected"),
    COW_MATERIALIZATION_FAILED(1402, "Cow materialization failed"),
    LOCK_VERSION_CONFLICT(1403, "Lock version conflict"),
    BAD_REQUEST(1001, "%s"),
    DATA_INTEGRITY_CONFLICT(1002, "The write conflicts with current persisted data");

    private final int code;
    private final String desc;

    WorkingsetResultCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
