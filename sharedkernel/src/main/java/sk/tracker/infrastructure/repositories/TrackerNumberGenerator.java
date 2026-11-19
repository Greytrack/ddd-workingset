package sk.tracker.infrastructure.repositories;

import sk.tracker.domain.TrackerId;

@FunctionalInterface
public interface TrackerNumberGenerator {
    String generate(TrackerId trackerId);
}
