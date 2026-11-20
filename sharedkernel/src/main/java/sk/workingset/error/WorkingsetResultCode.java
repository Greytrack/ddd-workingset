package sk.workingset.error;

import sk.common.error.DomainErrorType;
import sk.common.error.DomainResCode;

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
