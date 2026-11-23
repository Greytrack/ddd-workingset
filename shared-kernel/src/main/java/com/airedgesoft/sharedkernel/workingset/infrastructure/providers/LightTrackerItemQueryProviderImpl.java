package com.airedgesoft.sharedkernel.workingset.infrastructure.providers;


import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

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
            rs.getLong("local_id"),
            rs.getLong("common_id"),
            rs.getLong("branch_id"),
            rs.getLong("workingset_id"),
            rs.getInt("revision"),
            rs.getLong("source_local_id"),
            rs.getInt("source_revision"),
            rs.getBoolean("materialized"));

    private final JdbcTemplate jdbc;

    @Override
    public Optional<LightTrackerItemSummary> findById(Long localId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE local_id = ?",
                MAPPER, localId).stream().findFirst();
    }

    @Override
    public List<LightTrackerItemSummary> findInTracker(Long trackerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE branch_id = ? AND flags IS DISTINCT FROM 1 ORDER BY local_id",
                MAPPER, trackerId);
    }

    @Override
    public List<LightTrackerItemSummary> pageInTracker(Long trackerId, String afterLocalId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE branch_id = ? "
                        + "AND flags IS DISTINCT FROM 1 AND local_id > ? ORDER BY local_id LIMIT ?",
                MAPPER, trackerId, afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
    }

    @Override
    public List<LightTrackerItemSummary> pageInTracker(Long trackerId, long baselineAt, String afterLocalId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_ws_items_index WHERE branch_id = ? "
                        + "AND flags IS DISTINCT FROM 1 AND created_time <= ? AND local_id > ? ORDER BY local_id LIMIT ?",
                MAPPER, trackerId, baselineAt,
                afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
    }

    @Override
    public boolean existsInTracker(Long trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE branch_id = ?)",
                Boolean.class, trackerId));
    }

    @Override
    public boolean hasFamily(Long workingSetId, Long commonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?)",
                Boolean.class, workingSetId, commonId));
    }

    @Override
    public boolean hasSourceBackedDependents(Long localId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE source_local_id = ? AND has_copied = 0)",
                Boolean.class, localId));
    }
}
