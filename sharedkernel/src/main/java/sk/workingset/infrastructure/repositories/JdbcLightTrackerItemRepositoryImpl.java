package sk.workingset.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.*;
import sk.workingset.domain.repository.LightTrackerItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 的 PostgreSQL/Spring JDBC 仓储。
 *
 * <p>聚合级保存使用整行 upsert；三字段一致性由 {@link LightTrackerItem} 聚合行为保证。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcLightTrackerItemRepositoryImpl implements LightTrackerItemRepository {

    private static final String UPSERT = """
            INSERT INTO light_tracker_items
                (local_id, common_id, tracker_id, working_set_id, revision,
                 source_local_id, source_revision, materialized)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (local_id) DO UPDATE SET
                common_id = EXCLUDED.common_id,
                tracker_id = EXCLUDED.tracker_id,
                working_set_id = EXCLUDED.working_set_id,
                revision = EXCLUDED.revision,
                source_local_id = EXCLUDED.source_local_id,
                source_revision = EXCLUDED.source_revision,
                materialized = EXCLUDED.materialized
            """;

    private static final String COLUMNS =
            "local_id, common_id, tracker_id, working_set_id, revision, "
                    + "source_local_id, source_revision, materialized";

    private static final RowMapper<LightTrackerItem> MAPPER = (rs, rowNum) -> LightTrackerItem.restore(
            LocalId.of(rs.getLong("local_id")),
            CommonId.of(rs.getLong("common_id")),
            TrackerId.of(rs.getLong("tracker_id")),
            WorkingSetId.of(rs.getLong("working_set_id")),
            Revision.of(rs.getInt("revision")),
            LocalId.of(rs.getLong("source_local_id")),
            Revision.of(rs.getInt("source_revision")),
            rs.getBoolean("materialized"));

    private final JdbcTemplate jdbc;
    private final JdbcCommonInstanceStore commonInstances;


    @Override
    public void save(LightTrackerItem item) {
        jdbc.update(UPSERT,
                item.localId().numericValue(),
                item.commonId().numericValue(),
                item.trackerId().numericValue(),
                item.workingSetId().numericValue(),
                item.revision().value(),
                item.sourceLocalId().numericValue(),
                item.sourceRevision().value(),
                item.materialized());
    }

    @Override
    public Optional<LightTrackerItem> findById(LocalId localId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM light_tracker_items WHERE local_id = ?",
                MAPPER,
                localId.numericValue()).stream().findFirst();
    }

    @Override
    public List<LightTrackerItem> findInTracker(TrackerId trackerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM light_tracker_items WHERE tracker_id = ? ORDER BY local_id",
                MAPPER, trackerId.numericValue());
    }

    @Override
    public List<LightTrackerItem> pageInTracker(TrackerId trackerId, String afterLocalId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM light_tracker_items WHERE tracker_id = ? "
                        + "AND local_id > ? ORDER BY local_id LIMIT ?",
                MAPPER, trackerId.numericValue(), afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = RuntimeException.class)
    public boolean claimFamily(LightTrackerItem item) {
        boolean inserted = jdbc.update("""
                        INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                            source_local_id, source_revision, materialized)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (working_set_id, common_id) DO NOTHING
                        """, item.localId().numericValue(), item.commonId().numericValue(), item.trackerId().numericValue(),
                item.workingSetId().numericValue(), item.revision().value(), item.sourceLocalId().numericValue(),
                item.sourceRevision().value(), item.materialized()) == 1;
        if (inserted && !item.materialized()) {
            saveInheritedCommonInstance(item);
        }
        return inserted;
    }

    private void saveInheritedCommonInstance(LightTrackerItem item) {
        AirInstCommonPo common = jdbc.queryForObject("""
                SELECT target.work_space, source.number,
                       CASE WHEN source.revision = ? THEN COALESCE(source.name, source.title) END AS name,
                       CASE WHEN source.revision = ? THEN source.version END AS version
                FROM requirements source JOIN air_inst_tracker_rev target ON target.id = ?
                WHERE source.local_id = ? AND NOT EXISTS (SELECT 1 FROM requirements WHERE local_id = ?)
                """, (rs, row) -> new AirInstCommonPo(item.localId().numericValue(), 1401L, 140101L, 14010101L,
                        rs.getObject("work_space", Long.class), rs.getString("number"), rs.getString("name"),
                        null, rs.getString("version"), (long) item.revision().value(), 0,
                        null, null, null, null, null, null, null, item.commonId().numericValue(),
                        item.workingSetId().numericValue(), null, item.trackerId().numericValue(),
                        (long) item.revision().value()),
                item.sourceRevision().value(), item.sourceRevision().value(), item.trackerId().numericValue(),
                item.sourceLocalId().numericValue(), item.localId().numericValue());
        commonInstances.save(common);
    }

    @Override
    public boolean hasFamily(WorkingSetId workingSetId, CommonId commonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM light_tracker_items WHERE working_set_id = ? AND common_id = ?)
                """, Boolean.class, workingSetId.numericValue(), commonId.numericValue()));
    }

    @Override
    public boolean hasSourceBackedDependents(LocalId id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM light_tracker_items WHERE source_local_id = ? AND NOT materialized)
                """, Boolean.class, id.numericValue()));
    }

    @Override
    @Transactional
    public void remove(LocalId id) {
        long local = id.numericValue();
        List<Boolean> deleted = jdbc.queryForList(
                "DELETE FROM light_tracker_items WHERE local_id = ? RETURNING materialized", Boolean.class, local);
        if (!deleted.isEmpty() && !deleted.getFirst() && Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM requirements WHERE local_id = ?)", Boolean.class, local))) {
            commonInstances.remove(local);
        }
    }

    @Override
    public boolean existsInTracker(TrackerId trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM light_tracker_items WHERE tracker_id = ?)", Boolean.class, trackerId.numericValue()));
    }

    @Override
    public List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM light_tracker_items "
                        + "WHERE source_local_id = ? AND source_revision = ?",
                MAPPER,
                sourceLocalId.numericValue(),
                sourceRevision.value());
    }
}
