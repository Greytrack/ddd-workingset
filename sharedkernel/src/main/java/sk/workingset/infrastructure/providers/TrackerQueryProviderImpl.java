package sk.workingset.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import sk.RequestThreadHolder;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.List;

/**
 * Tracker 只读布尔查询的 JDBC 适配器。
 */
@Component
@RequiredArgsConstructor
public class TrackerQueryProviderImpl implements TrackerQueryProvider {
    private final JdbcTemplate jdbc;

    @Override
    public boolean isVisible(TrackerId trackerId) {
        return exists("id = ? AND visible = 1 AND work_space = COALESCE(CAST(? AS bigint), work_space)",
                trackerId.numericValue(), RequestThreadHolder.getWorkspace());
    }

    @Override
    public boolean hasOrgChildren(TrackerId id) {
        return exists("parent_id = ?", id.numericValue());
    }

    @Override
    public boolean hasDerivedChildren(TrackerId id) {
        return exists("source_branch_id = ?", id.numericValue());
    }

    @Override
    public List<TrackerId> findTrackerIdsInSpace(String spaceId) {
        return jdbc.queryForList("""
                SELECT id FROM air_inst_tracker_rev WHERE work_space = ? ORDER BY id
                """, Long.class, Long.valueOf(spaceId)).stream().map(TrackerId::of).toList();
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
    public boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId) {
        return exists("workingset_id = ? AND work_space = ? AND tracker_class = ? AND is_default = 1",
                workingSetId.numericValue(), Long.valueOf(spaceId), Long.valueOf(classificationId));
    }

    @Override
    public java.util.Optional<TrackerId> findDefaultTrackerId(WorkingSetId workingSetId, long spaceId,
                                                              long classificationId) {
        return jdbc.queryForList("""
                        SELECT id FROM air_inst_tracker_rev
                        WHERE delete_flag = 0 AND visible = 1 AND workingset_id = ? AND work_space = ?
                            AND tracker_class = ? AND is_default = 1
                        ORDER BY id
                        """, Long.class, workingSetId.numericValue(), spaceId, classificationId).stream()
                .findFirst().map(TrackerId::of);
    }

    private boolean exists(String predicate, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_inst_tracker_rev WHERE delete_flag = 0 AND " + predicate + ")",
                Boolean.class, args));
    }
}
