package sk.workingset.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.LightTrackerItemSummary;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSetId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 查询适配器；只读，不重建聚合。
 */
@Component
@RequiredArgsConstructor
public class LightTrackerItemQueryProviderImpl implements LightTrackerItemQueryProvider {
    private static final String COLUMNS =
            "local_id, common_id, branch_id, workingset_id, revision, source_local_id, source_revision, (has_copied = 1) AS materialized";
    private static final RowMapper<LightTrackerItemSummary> MAPPER = (rs, rowNum) -> new LightTrackerItemSummary(
            LocalId.of(rs.getLong("local_id")),
            CommonId.of(rs.getLong("common_id")),
            TrackerId.of(rs.getLong("branch_id")),
            WorkingSetId.of(rs.getLong("workingset_id")),
            Revision.of(rs.getInt("revision")),
            LocalId.of(rs.getLong("source_local_id")),
            Revision.of(rs.getInt("source_revision")),
            rs.getBoolean("materialized"));

    private final JdbcTemplate jdbc;

    @Override
    public Optional<LightTrackerItemSummary> findById(LocalId localId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE local_id = ?",
                MAPPER, localId.numericValue()).stream().findFirst();
    }

    @Override
    public List<LightTrackerItemSummary> findInTracker(TrackerId trackerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE branch_id = ? AND flags IS DISTINCT FROM 1 ORDER BY local_id",
                MAPPER, trackerId.numericValue());
    }

    @Override
    public List<LightTrackerItemSummary> pageInTracker(TrackerId trackerId, String afterLocalId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE branch_id = ? "
                        + "AND flags IS DISTINCT FROM 1 AND local_id > ? ORDER BY local_id LIMIT ?",
                MAPPER, trackerId.numericValue(), afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
    }

    @Override
    public List<LightTrackerItemSummary> pageInTracker(TrackerId trackerId, Instant baselineAt, String afterLocalId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE branch_id = ? "
                        + "AND flags IS DISTINCT FROM 1 AND created_time <= ? AND local_id > ? ORDER BY local_id LIMIT ?",
                MAPPER, trackerId.numericValue(), baselineAt.toEpochMilli(),
                afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
    }

    @Override
    public boolean existsInTracker(TrackerId trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE branch_id = ?)",
                Boolean.class, trackerId.numericValue()));
    }

    @Override
    public boolean hasFamily(WorkingSetId workingSetId, CommonId commonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?)",
                Boolean.class, workingSetId.numericValue(), commonId.numericValue()));
    }

    @Override
    public boolean hasSourceBackedDependents(LocalId localId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE source_local_id = ? AND has_copied = 0)",
                Boolean.class, localId.numericValue()));
    }
}
