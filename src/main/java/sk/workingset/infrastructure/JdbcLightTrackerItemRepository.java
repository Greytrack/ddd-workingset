package sk.workingset.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * LightTrackerItem 的 PostgreSQL/Spring JDBC 仓储。
 *
 * <p>聚合级保存使用整行 upsert；三字段一致性由 {@link LightTrackerItem} 聚合行为保证。
 */
public class JdbcLightTrackerItemRepository implements LightTrackerItemRepository {

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
            LocalId.of(rs.getString("local_id")),
            CommonId.of(rs.getString("common_id")),
            TrackerId.of(rs.getString("tracker_id")),
            WorkingSetId.of(rs.getString("working_set_id")),
            Revision.of(rs.getInt("revision")),
            LocalId.of(rs.getString("source_local_id")),
            Revision.of(rs.getInt("source_revision")),
            rs.getBoolean("materialized"));

    private final JdbcTemplate jdbc;

    public JdbcLightTrackerItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(LightTrackerItem item) {
        jdbc.update(UPSERT,
                item.localId().value(),
                item.commonId().value(),
                item.trackerId().value(),
                item.workingSetId().value(),
                item.revision().value(),
                item.sourceLocalId().value(),
                item.sourceRevision().value(),
                item.materialized());
    }

    @Override
    public Optional<LightTrackerItem> findById(LocalId localId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM light_tracker_items WHERE local_id = ?",
                MAPPER,
                localId.value()).stream().findFirst();
    }

    @Override
    public List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM light_tracker_items "
                        + "WHERE source_local_id = ? AND source_revision = ?",
                MAPPER,
                sourceLocalId.value(),
                sourceRevision.value());
    }
}
