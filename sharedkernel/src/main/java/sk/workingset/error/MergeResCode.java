package sk.workingset.error;

import sk.common.error.DomainErrorType;
import sk.common.error.DomainResCode;

/**
 * T4 Merge codes with their public reason values.
 */
public enum MergeResCode implements DomainResCode {
    NOT_ALLOWED(DomainErrorType.CONFLICT, 1301, "Merge not allowed", "MergeNotAllowed"),
    ROUND_NOT_FOUND(DomainErrorType.NOT_FOUND, 1302, "Round not found", "RoundNotFound"),
    ROUND_CLOSED(DomainErrorType.CONFLICT, 1303, "Round closed", "RoundClosed"),
    ROUND_IN_PROGRESS(DomainErrorType.CONFLICT, 1304, "Round in progress", "RoundInProgress"),
    PAIR_MERGE_IN_PROGRESS(DomainErrorType.CONFLICT, 1305, "Pair merge in progress", "PairMergeInProgress"),
    STALE_EVIDENCE(DomainErrorType.ABORTED, 1306, "Stale evidence", "StaleEvidence"),
    INVALID_DECISION(DomainErrorType.UNPROCESSABLE_ENTITY, 1307, "Invalid decision", "InvalidDecision"),
    REJECTED(DomainErrorType.UNPROCESSABLE_ENTITY, 1308, "Rejected", "Rejected"),
    CONFLICT(DomainErrorType.CONFLICT, 1309, "Conflict", "Conflict");

    private final DomainErrorType type;
    private final int code;
    private final String desc;
    private final String reason;

    MergeResCode(DomainErrorType type, int code, String desc, String reason) {
        this.type = type;
        this.code = code;
        this.desc = desc;
        this.reason = reason;
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

    public String reason() {
        return reason;
    }
}
