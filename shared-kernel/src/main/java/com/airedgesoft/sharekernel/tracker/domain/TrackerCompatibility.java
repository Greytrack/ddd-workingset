package com.airedgesoft.sharekernel.tracker.domain;

import org.jmolecules.ddd.annotation.Entity;

/**
 * Identity and classification anchor of the legacy row owned by Tracker.
 */
@Entity
public final class TrackerCompatibility {
    private final Long trackerId;
    private final long originalClassificationId;

    TrackerCompatibility(Long trackerId, long originalClassificationId) {
        this.trackerId = trackerId;
        this.originalClassificationId = originalClassificationId;
    }

    public Long trackerId() {
        return trackerId;
    }

    public long originalClassificationId() {
        return originalClassificationId;
    }
}
