package sk.workingset.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Tracker 聚合身份（D-001：Tracker 独立聚合）。
 */
public record TrackerId(@JsonProperty("value") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericValue) {

    public TrackerId {
        Objects.requireNonNull(numericValue, "trackerId");
        if (numericValue <= 0) {
            throw new IllegalArgumentException("trackerId must be a positive integer");
        }
    }

    public static TrackerId of(String value) {
        return of(Long.parseLong(value));
    }

    public static TrackerId of(long value) {
        return new TrackerId(value);
    }

    public String value() {
        return numericValue.toString();
    }
}
