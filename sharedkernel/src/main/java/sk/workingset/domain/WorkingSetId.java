package sk.workingset.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Working Set 身份。
 */
@ValueObject
public record WorkingSetId(@JsonProperty("value") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericValue) {

    public WorkingSetId {
        Objects.requireNonNull(numericValue, "workingSetId");
        if (numericValue <= 0) {
            throw new IllegalArgumentException("workingSetId must be a positive integer");
        }
    }

    public static WorkingSetId of(String value) {
        return of(Long.parseLong(value));
    }

    public static WorkingSetId of(long value) {
        return new WorkingSetId(value);
    }

    public String value() {
        return numericValue.toString();
    }
}
