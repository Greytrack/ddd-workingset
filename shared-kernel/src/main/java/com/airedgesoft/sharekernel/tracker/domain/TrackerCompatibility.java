package com.airedgesoft.sharekernel.tracker.domain;

import org.jmolecules.ddd.annotation.Entity;

/**
 * Identity and classification anchor of the legacy row owned by Tracker.
 */
@Entity
public final class TrackerCompatibility {
    private final TrackerId trackerId;
    private final long originalClassificationId;

    TrackerCompatibility(TrackerId trackerId, long originalClassificationId) {
        this.trackerId = trackerId;
        this.originalClassificationId = originalClassificationId;
    }

    public TrackerId trackerId() {
        return trackerId;
    }

    public long originalClassificationId() {
        return originalClassificationId;
    }
}
