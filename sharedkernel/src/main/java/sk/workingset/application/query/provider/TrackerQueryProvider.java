package sk.workingset.application.query.provider;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** Tracker 的只读存在性/可见性查询；命令侧聚合重建仍走 TrackerRepository。 */
public interface TrackerQueryProvider {
    boolean isVisible(TrackerId trackerId);

    boolean hasOrgChildren(TrackerId id);

    boolean hasDerivedChildren(TrackerId id);

    boolean hasTrackersInSpace(String spaceId);

    boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId);
}
