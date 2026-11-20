package sk.workingset.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import sk.RequestThreadHolder;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.domain.workingset.WorkingSetId;

/** Tracker 只读布尔查询的 JDBC 适配器。 */
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
    public boolean hasTrackersInSpace(String spaceId) {
        return exists("work_space = ?", Long.valueOf(spaceId));
    }

    @Override
    public boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId) {
        return exists("workingset_id = ? AND work_space = ? AND tracker_class = ? AND is_default = 1",
                workingSetId.numericValue(), Long.valueOf(spaceId), Long.valueOf(classificationId));
    }

    private boolean exists(String predicate, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_inst_tracker_rev WHERE delete_flag = 0 AND " + predicate + ")",
                Boolean.class, args));
    }
}
