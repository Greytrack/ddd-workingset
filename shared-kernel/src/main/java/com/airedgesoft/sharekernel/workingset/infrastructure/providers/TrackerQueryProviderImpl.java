package com.airedgesoft.sharekernel.workingset.infrastructure.providers;


import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerQueryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Tracker 只读布尔查询的 JDBC 适配器。
 */
@Component
@RequiredArgsConstructor
public class TrackerQueryProviderImpl implements TrackerQueryProvider {
    private final JdbcTemplate jdbc;

    @Override
    public boolean hasOwnedTrackers(Long workingSetId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM air_inst_tracker_rev WHERE workingset_id = ?)
                """, Boolean.class, workingSetId));
    }

    @Override
    public boolean hasOrgChildren(Long id) {
        return exists("parent_id = ?", id);
    }

    @Override
    public boolean hasDerivedChildren(Long id) {
        return exists("source_branch_id = ?", id);
    }

    @Override
    public boolean hasRegisteredBusinessItems(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM air_inst_common WHERE tracker = ?)
                """, Boolean.class, id));
    }

    @Override
    public List<Long> findTrackerIdsInSpace(String spaceId) {
        return jdbc.queryForList("""
                SELECT id FROM air_inst_tracker_rev WHERE work_space = ? ORDER BY id
                """, Long.class, Long.valueOf(spaceId)).stream().toList();
    }

    @Override
    public boolean hasActiveTrackersInSpace(String spaceId) {
        return exists("work_space = ?", Long.valueOf(spaceId));
    }

    @Override
    public boolean hasExternalTrackerReferences(String spaceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM air_inst_tracker_rev tracker
                    JOIN air_inst_tracker_rev child
                      ON child.source_branch_id = tracker.id OR child.parent_id = tracker.id
                    WHERE tracker.work_space = ? AND child.work_space <> tracker.work_space
                )
                """, Boolean.class, Long.valueOf(spaceId)));
    }

    @Override
    public boolean existsDefaultTracker(Long workingSetId, String spaceId, String classificationId) {
        return exists("workingset_id = ? AND work_space = ? AND tracker_class = ? AND is_default = 1",
                workingSetId, Long.valueOf(spaceId), Long.valueOf(classificationId));
    }

    @Override
    public Optional<Long> findDefaultTrackerId(Long workingSetId, long spaceId,
                                                    long classificationId) {
        return jdbc.queryForList("""
                        SELECT id FROM air_inst_tracker_rev
                        WHERE delete_flag = 0 AND workingset_id = ? AND work_space = ?
                            AND tracker_class = ? AND is_default = 1
                        ORDER BY id
                        """, Long.class, workingSetId, spaceId, classificationId).stream()
                .findFirst();
    }

    private boolean exists(String predicate, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_inst_tracker_rev WHERE delete_flag = 0 AND " + predicate + ")",
                Boolean.class, args));
    }
}
