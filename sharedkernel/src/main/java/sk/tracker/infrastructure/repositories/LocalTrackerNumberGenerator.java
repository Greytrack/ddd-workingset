package sk.tracker.infrastructure.repositories;

import org.springframework.stereotype.Component;
import sk.tracker.domain.TrackerId;

/** Temporary deterministic implementation until the platform numbering service is wired. */
@Component
public class LocalTrackerNumberGenerator implements TrackerNumberGenerator {
    @Override
    public String generate(TrackerId trackerId) {
        return "TRK-" + trackerId.value();
    }
}
