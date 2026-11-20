package alm.requirement.domain;

import sk.common.error.DomainErrorType;
import sk.common.error.DomainResCode;

public enum RequirementResultCode implements DomainResCode {
    LOCK_VERSION_CONFLICT(DomainErrorType.ABORTED, 1403, "Lock version conflict"),
    ALREADY_ACTIVE(DomainErrorType.INVALID_ARGUMENT, 1404, "Requirement is already active"),
    LOCK_VERSION_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 1405, "expectedLockVersion is required for delete/undelete"),
    DELETED(DomainErrorType.INTERNAL, 1406, "Requirement is deleted"),
    TITLE_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 1407, "title must not be blank"),
    SOFT_DELETE_REQUIRED(DomainErrorType.CONFLICT, 1408, "Soft delete is required before permanent deletion");

    private final DomainErrorType type;
    private final int code;
    private final String desc;

    RequirementResultCode(DomainErrorType type, int code, String desc) {
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
