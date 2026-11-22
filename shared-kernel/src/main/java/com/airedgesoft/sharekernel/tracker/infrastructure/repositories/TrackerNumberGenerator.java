package com.airedgesoft.sharekernel.tracker.infrastructure.repositories;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;

@FunctionalInterface
public interface TrackerNumberGenerator {
    String generate(TrackerId trackerId);
}
