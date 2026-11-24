package com.airedgesoft.sharedkernel.workingset.adapters;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;

/** Resolves the HTTP idempotency key used by the persisted task and merge round. */
public final class WorkingsetRequestKey {
    private static final int MAX_LENGTH = 128;

    private WorkingsetRequestKey() {
    }

    public static String resolve(String header, String requestId) {
        String key = header == null ? requestId : header;
        if (key == null || key.isBlank() || key.length() > MAX_LENGTH) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST,
                    "Idempotency-Key must contain between 1 and " + MAX_LENGTH + " characters");
        }
        if (header != null && requestId != null && !key.equals(requestId)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "Idempotency-Key must match requestId");
        }
        return key;
    }
}
