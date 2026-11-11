package sk.workingset.domain;

import java.util.Objects;

/**
 * Tracker 聚合身份（D-001：Tracker 独立聚合）。
 */
public record TrackerId(String value) {

    public TrackerId {
        Objects.requireNonNull(value, "trackerId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("trackerId must not be blank");
        }
    }

    public static TrackerId of(String value) {
        return new TrackerId(value);
    }
}
