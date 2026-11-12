package sk.workingset.application.port;

import java.util.Optional;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;

public interface TrackerRepository {
    Optional<Tracker> lockTracker(TrackerId id);
    void insertTracker(Tracker tracker);
    boolean hasOrgChildren(TrackerId id);
    boolean hasDerivedChildren(TrackerId id);
    boolean hasTrackersInSpace(String spaceId);
    void deleteTracker(TrackerId id);
}
