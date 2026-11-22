package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;

import java.util.List;
import java.util.Optional;

/**
 * Tracker 的只读查询；命令侧聚合重建仍走 TrackerRepository。
 */
public interface TrackerQueryProvider {
    boolean hasOwnedTrackers(WorkingSetId workingSetId);

    boolean hasOrgChildren(TrackerId id);

    boolean hasDerivedChildren(TrackerId id);

    List<TrackerId> findTrackerIdsInSpace(String spaceId);

    boolean hasExternalTrackerReferences(String spaceId);

    boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId);

    Optional<TrackerId> findDefaultTrackerId(WorkingSetId workingSetId, long spaceId, long classificationId);
}
