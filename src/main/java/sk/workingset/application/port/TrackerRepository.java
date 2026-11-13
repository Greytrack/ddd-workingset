package sk.workingset.application.port;

import java.util.Optional;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;

/** Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。 */
public interface TrackerRepository {
    Optional<Tracker> findTracker(TrackerId id);
    Optional<Tracker> lockTracker(TrackerId id);
    void insertTracker(Tracker tracker);
    boolean hasOrgChildren(TrackerId id);
    boolean hasDerivedChildren(TrackerId id);
    boolean hasTrackersInSpace(String spaceId);
    void deleteTracker(TrackerId id);
}
