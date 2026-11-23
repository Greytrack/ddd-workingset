package com.airedgesoft.sharedkernel.workingset.application;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.DomainResCode;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;

/**
 * 跨 BC 公共失败分类（T4 §5）。
 */
public enum FailureReason implements DomainResCode {
    NOT_FOUND(WorkingsetResultCode.APPLICATION_NOT_FOUND),
    NOT_INCLUDED(WorkingsetResultCode.APPLICATION_NOT_INCLUDED),
    DENIED(WorkingsetResultCode.APPLICATION_DENIED),
    DELETED(WorkingsetResultCode.APPLICATION_DELETED),
    HISTORY_UNAVAILABLE(WorkingsetResultCode.APPLICATION_HISTORY_UNAVAILABLE),
    PREPARING(WorkingsetResultCode.APPLICATION_PREPARING),
    CAPABILITY_NOT_SUPPORTED(WorkingsetResultCode.APPLICATION_CAPABILITY_NOT_SUPPORTED),
    DEPENDENCY_UNAVAILABLE(WorkingsetResultCode.APPLICATION_DEPENDENCY_UNAVAILABLE),
    UNKNOWN(WorkingsetResultCode.APPLICATION_UNKNOWN);

    private final WorkingsetResultCode resultCode;

    FailureReason(WorkingsetResultCode resultCode) {
        this.resultCode = resultCode;
    }

    /**
     * Reads the application reason from a DomainException without relying on a subclass.
     */
    public static FailureReason of(DomainException failure) {
        return failure.getResCode() instanceof FailureReason reason ? reason : UNKNOWN;
    }

    public WorkingsetResultCode resultCode() {
        return resultCode;
    }

    @Override
    public DomainErrorType getType() {
        return resultCode.getType();
    }

    @Override
    public int getCode() {
        return resultCode.getCode();
    }

    @Override
    public String getDesc() {
        return resultCode.getDesc();
    }
}
