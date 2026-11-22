package com.airedgesoft.sharekernel.workingset.application.query.provider;


import java.util.List;
import java.util.Optional;

/**
 * Tracker 的只读查询；命令侧聚合重建仍走 TrackerRepository。
 */
public interface TrackerQueryProvider {
    boolean hasOwnedTrackers(Long workingSetId);

    boolean hasOrgChildren(Long id);

    boolean hasDerivedChildren(Long id);

    List<Long> findTrackerIdsInSpace(String spaceId);

    boolean hasExternalTrackerReferences(String spaceId);

    boolean existsDefaultTracker(Long workingSetId, String spaceId, String classificationId);

    Optional<Long> findDefaultTrackerId(Long workingSetId, long spaceId, long classificationId);
}
