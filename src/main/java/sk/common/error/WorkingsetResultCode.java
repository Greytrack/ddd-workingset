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
public enum WorkingsetResultCode implements DomainResCode {
    APPLICATION_NOT_FOUND(DomainErrorType.NOT_FOUND, 1101, "Not found"),
    APPLICATION_NOT_INCLUDED(DomainErrorType.FORBIDDEN, 1102, "Not included"),
    APPLICATION_DENIED(DomainErrorType.FORBIDDEN, 1103, "Denied"),
    APPLICATION_DELETED(DomainErrorType.GONE, 1104, "Deleted"),
    APPLICATION_HISTORY_UNAVAILABLE(DomainErrorType.CONFLICT, 1105, "History unavailable"),
    APPLICATION_PREPARING(DomainErrorType.CONFLICT, 1106, "Preparing"),
    APPLICATION_CAPABILITY_NOT_SUPPORTED(DomainErrorType.UNPROCESSABLE_ENTITY, 1107, "Capability not supported"),
    APPLICATION_DEPENDENCY_UNAVAILABLE(DomainErrorType.UNAVAILABLE, 1108, "Dependency unavailable"),
    APPLICATION_UNKNOWN(DomainErrorType.UNAVAILABLE, 1109, "Unknown"),
    WORKING_SET_NOT_EXISTS(DomainErrorType.NOT_FOUND, 1201, "Working Set does not exist"),
    WORKING_SET_NOT_WRITABLE(DomainErrorType.CONFLICT, 1202, "Working set not writable"),
    WORKING_SET_CYCLE(DomainErrorType.CONFLICT, 1203, "Working set cycle"),
    WORKING_SET_ALREADY_EXISTS(DomainErrorType.ALREADY_EXISTS, 1204, "Working set already exists"),
    WORKING_SET_PARENT_IMMUTABLE(DomainErrorType.CONFLICT, 1205, "Working set parent immutable"),
    WORKING_SETS_NOT_ADJACENT(DomainErrorType.CONFLICT, 1206, "Working sets not adjacent"),
    WORKING_SET_HAS_CHILDREN(DomainErrorType.CONFLICT, 1207, "Working set has children"),
    PRODUCT_SPACE_NOT_FOUND(DomainErrorType.NOT_FOUND, 1208, "Product space not found"),
    ALREADY_BOUND(DomainErrorType.CONFLICT, 1209, "Already bound"),
    MISSING_SPACE_BINDING(DomainErrorType.CONFLICT, 1210, "Missing space binding"),
    TRACKER_ALREADY_BOUND(DomainErrorType.CONFLICT, 1211, "Tracker already bound"),
    SLOT_OCCUPIED(DomainErrorType.CONFLICT, 1212, "Slot occupied"),
    SOURCE_TRACKER_NOT_FOUND(DomainErrorType.NOT_FOUND, 1213, "Source tracker not found"),
    SHARING_MODE_MISMATCH(DomainErrorType.UNPROCESSABLE_ENTITY, 1214, "Sharing mode mismatch"),
    TRACKER_OUTSIDE_SLOT(DomainErrorType.UNPROCESSABLE_ENTITY, 1215, "Tracker outside slot"),
    SOURCE_ITEM_NOT_FOUND(DomainErrorType.NOT_FOUND, 1216, "Source item not found"),
    SOURCE_ITEM_MISMATCH(DomainErrorType.UNPROCESSABLE_ENTITY, 1217, "Source item mismatch"),
    TRACKER_NOT_FOUND(DomainErrorType.NOT_FOUND, 1218, "Tracker not found"),
    TRACKER_BINDING_NOT_FOUND(DomainErrorType.NOT_FOUND, 1219, "Tracker binding not found"),
    SPACE_BINDING_NOT_FOUND(DomainErrorType.NOT_FOUND, 1220, "Space binding not found"),
    SPACE_STILL_HAS_TRACKERS(DomainErrorType.CONFLICT, 1221, "Space still has trackers"),
    SPACE_STILL_BOUND_IN_WORKING_SET(DomainErrorType.CONFLICT, 1222, "Space still bound in working set"),
    TRACKER_HAS_ITEMS(DomainErrorType.CONFLICT, 1223, "Tracker has items"),
    TRACKER_HAS_ORG_CHILDREN(DomainErrorType.CONFLICT, 1224, "Tracker has org children"),
    TRACKER_HAS_DERIVED_CHILDREN(DomainErrorType.CONFLICT, 1225, "Tracker has derived children"),
    DEFAULT_WORKING_SET_UNDELETABLE(DomainErrorType.CONFLICT, 1226, "Default working set undeletable"),
    MEMBER_CANNOT_BE_UNBOUND(DomainErrorType.CONFLICT, 1227, "Member cannot be unbound"),
    NO_DERIVABLE_TRACKER(DomainErrorType.CONFLICT, 1228, "No derivable tracker"),
    MERGE_MERGE_NOT_ALLOWED(DomainErrorType.CONFLICT, 1301, "Merge not allowed"),
    MERGE_ROUND_NOT_FOUND(DomainErrorType.NOT_FOUND, 1302, "Round not found"),
    MERGE_ROUND_CLOSED(DomainErrorType.CONFLICT, 1303, "Round closed"),
    MERGE_ROUND_IN_PROGRESS(DomainErrorType.CONFLICT, 1304, "Round in progress"),
    MERGE_PAIR_MERGE_IN_PROGRESS(DomainErrorType.CONFLICT, 1305, "Pair merge in progress"),
    MERGE_STALE_EVIDENCE(DomainErrorType.ABORTED, 1306, "Stale evidence"),
    MERGE_INVALID_DECISION(DomainErrorType.UNPROCESSABLE_ENTITY, 1307, "Invalid decision"),
    MERGE_REJECTED(DomainErrorType.UNPROCESSABLE_ENTITY, 1308, "Rejected"),
    MERGE_CONFLICT(DomainErrorType.CONFLICT, 1309, "Conflict"),
    MATERIALIZATION_REJECTED(DomainErrorType.CONFLICT, 1401, "Materialization rejected"),
    COW_MATERIALIZATION_FAILED(DomainErrorType.INTERNAL, 1402, "Cow materialization failed"),
    BAD_REQUEST(DomainErrorType.INVALID_ARGUMENT, 1001, "%s"),
    INVALID_DOMAIN_STATE(DomainErrorType.INTERNAL, 1003, "%s"),
    DATA_INTEGRITY_CONFLICT(DomainErrorType.CONFLICT, 1002, "The write conflicts with current persisted data");

    private final DomainErrorType type;
    private final int code;
    private final String desc;

    WorkingsetResultCode(DomainErrorType type, int code, String desc) {
        this.type = type;
        this.code = code;
        this.desc = desc;
    }

    @Override
    public DomainErrorType getType() {
        return type;
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
