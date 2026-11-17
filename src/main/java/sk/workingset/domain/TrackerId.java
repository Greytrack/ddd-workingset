package sk.workingset.domain;

import sk.common.error.DomainException;
import sk.common.error.WorkingsetResultCode;

import java.util.Objects;

/**
 * Tracker 聚合身份（D-001：Tracker 独立聚合）。
 */
public record TrackerId(String value) {

    public TrackerId {
        Objects.requireNonNull(value, "trackerId");
        if (value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "trackerId must not be blank");
        }
    }

    public static TrackerId of(String value) {
        return new TrackerId(value);
    }
}
