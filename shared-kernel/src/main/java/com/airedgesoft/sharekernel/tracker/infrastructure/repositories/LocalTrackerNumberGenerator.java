package com.airedgesoft.sharekernel.tracker.infrastructure.repositories;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import org.springframework.stereotype.Component;

/**
 * Temporary deterministic implementation until the platform numbering service is wired.
 */
@Component
public class LocalTrackerNumberGenerator implements TrackerNumberGenerator {
    @Override
    public String generate(TrackerId trackerId) {
        return "TRK-" + trackerId.value();
    }
}
