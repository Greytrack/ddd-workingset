package sk.workingset.domain;

import java.util.Objects;

/**
 * Working Set 身份。
 */
public record WorkingSetId(String value) {

    public WorkingSetId {
        Objects.requireNonNull(value, "workingSetId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("workingSetId must not be blank");
        }
    }

    public static WorkingSetId of(String value) {
        return new WorkingSetId(value);
    }
}
