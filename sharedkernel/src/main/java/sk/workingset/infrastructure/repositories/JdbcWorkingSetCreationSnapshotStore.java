package sk.workingset.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import sk.workingset.domain.workingset.WorkingSetCreationSnapshotStore;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 创建任务受理时冻结的父 Item 身份快照（PostgreSQL）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcWorkingSetCreationSnapshotStore implements WorkingSetCreationSnapshotStore {
    private final JdbcTemplate jdbc;


    @Override
    public void save(String creationId, List<Item> items) {
        jdbc.update("""
                INSERT INTO working_set_creation_snapshots (creation_id, completed_at)
                VALUES (?, ?) ON CONFLICT (creation_id) DO NOTHING
                """, Long.valueOf(creationId), Timestamp.from(Instant.now()));
        if (!items.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO working_set_creation_items
                        (creation_id, parent_tracker_id, local_id, common_id, revision,
                         source_local_id, source_revision)
                    VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, items, items.size(), (statement, item) -> {
                statement.setLong(1, Long.parseLong(creationId));
                statement.setLong(2, Long.parseLong(item.parentTrackerId()));
                statement.setLong(3, item.localId().numericValue());
                statement.setLong(4, item.commonId().numericValue());
                statement.setInt(5, item.revision().value());
                statement.setLong(6, item.sourceLocalId().numericValue());
                statement.setInt(7, item.sourceRevision().value());
            });
        }
    }

    @Override
    public List<Item> page(String creationId, String parentTrackerId, String afterLocalId, int limit) {
        return jdbc.query("""
                        SELECT parent_tracker_id, local_id, common_id, revision, source_local_id, source_revision
                        FROM working_set_creation_items
                        WHERE creation_id = ? AND parent_tracker_id = ? AND local_id > ?
                        ORDER BY local_id LIMIT ?
                        """, (rs, row) -> new Item(rs.getString("parent_tracker_id"),
                        sk.workingset.domain.LocalId.of(rs.getLong("local_id")),
                        sk.workingset.domain.CommonId.of(rs.getLong("common_id")),
                        sk.workingset.domain.Revision.of(rs.getInt("revision")),
                        sk.workingset.domain.LocalId.of(rs.getLong("source_local_id")),
                        sk.workingset.domain.Revision.of(rs.getInt("source_revision"))),
                Long.valueOf(creationId), Long.valueOf(parentTrackerId), afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
    }

    @Override
    public boolean contains(String creationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM working_set_creation_snapshots WHERE creation_id = ?)
                """, Boolean.class, Long.valueOf(creationId)));
    }
}
