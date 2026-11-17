package sk.workingset.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import sk.workingset.application.port.WorkingSetCreationSnapshotStore;

/** 创建任务受理时冻结的父 Item 身份快照（PostgreSQL）。 */
public final class JdbcWorkingSetCreationSnapshotStore implements WorkingSetCreationSnapshotStore {
    private final JdbcTemplate jdbc;

    public JdbcWorkingSetCreationSnapshotStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String creationId, List<Item> items) {
        jdbc.update("""
                INSERT INTO working_set_creation_snapshots (creation_id, completed_at)
                VALUES (?, ?) ON CONFLICT (creation_id) DO NOTHING
                """, creationId, Timestamp.from(Instant.now()));
        if (!items.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO working_set_creation_items
                        (creation_id, parent_tracker_id, local_id, common_id, revision,
                         source_local_id, source_revision)
                    VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, items, items.size(), (statement, item) -> {
                        statement.setString(1, creationId);
                        statement.setString(2, item.parentTrackerId());
                        statement.setString(3, item.localId().value());
                        statement.setString(4, item.commonId().value());
                        statement.setInt(5, item.revision().value());
                        statement.setString(6, item.sourceLocalId().value());
                        statement.setInt(7, item.sourceRevision().value());
                    });
        }
    }

    @Override
    public List<Item> page(String creationId, String parentTrackerId, String afterLocalId, int limit) {
        return jdbc.query("""
                SELECT parent_tracker_id, local_id, common_id, revision, source_local_id, source_revision
                FROM working_set_creation_items
                WHERE creation_id = ? AND parent_tracker_id = ? AND local_id COLLATE "C" > ?
                ORDER BY local_id COLLATE "C" LIMIT ?
                """, (rs, row) -> new Item(rs.getString("parent_tracker_id"),
                sk.workingset.domain.LocalId.of(rs.getString("local_id")),
                sk.workingset.domain.CommonId.of(rs.getString("common_id")),
                sk.workingset.domain.Revision.of(rs.getInt("revision")),
                sk.workingset.domain.LocalId.of(rs.getString("source_local_id")),
                sk.workingset.domain.Revision.of(rs.getInt("source_revision"))),
                creationId, parentTrackerId, afterLocalId, limit);
    }

    @Override
    public boolean contains(String creationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM working_set_creation_snapshots WHERE creation_id = ?)
                """, Boolean.class, creationId));
    }
}
