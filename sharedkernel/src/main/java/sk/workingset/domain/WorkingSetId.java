package sk.workingset.domain;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

import java.util.Objects;

/**
 * Working Set 身份。
 */
public record WorkingSetId(String value) {

    public WorkingSetId {
        Objects.requireNonNull(value, "workingSetId");
        if (value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "workingSetId must not be blank");
        }
    }

    public static WorkingSetId of(String value) {
        return new WorkingSetId(value);
    }
}
