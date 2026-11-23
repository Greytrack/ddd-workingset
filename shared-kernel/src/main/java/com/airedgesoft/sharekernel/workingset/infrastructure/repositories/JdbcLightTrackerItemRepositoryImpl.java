package com.airedgesoft.sharekernel.workingset.infrastructure.repositories;


import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.SharedKernelResultCode;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRevisionEntity;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 的 PostgreSQL/Spring JDBC 仓储。
 *
 * <p>聚合级保存使用整行 upsert；三字段一致性由 {@link LightTrackerItem} 聚合行为保证。
 * {@code sub_class_id}/{@code work_space_id} 由聚合带入，不再从公共摘要读取。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcLightTrackerItemRepositoryImpl implements LightTrackerItemRepository {

    private static final String INSERT = """
            INSERT INTO air_ws_items_index
                (id, local_id, common_id, branch_id, workingset_id, revision, source_local_id, source_revision,
                 has_copied, flags, work_space_id, master_id, parent_branch_id, sub_class_id, created_by, created_time)
            SELECT ?, ?, ?, t.id, t.workingset_id, ?, ?, ?, ?, ?,
                   t.work_space, t.common_id, t.source_branch_id, t.tracker_class, ?, ?
            FROM air_inst_tracker_rev t
            WHERE t.id = ? AND t.workingset_id = ?
              AND t.work_space = ? AND t.tracker_class = ?
            """;

    private static final String COLUMNS = """
            i.local_id, i.common_id, i.branch_id, i.workingset_id, i.revision, i.source_local_id,
            i.source_revision, (i.has_copied = 1) AS materialized, (i.flags = 1) AS deleted,
            i.work_space_id, i.sub_class_id, i.created_by,
            ARRAY(SELECT r.revision FROM air_ws_items_revision_index r
                WHERE r.local_id = i.local_id ORDER BY r.revision) AS indexed_revisions
            """;

    private static final RowMapper<LightTrackerItem> MAPPER = (rs, rowNum) -> LightTrackerItem.restore(
            rs.getLong("local_id"),
            rs.getLong("common_id"),
            rs.getLong("branch_id"),
            rs.getLong("workingset_id"),
            rs.getInt("revision"),
            rs.getLong("source_local_id"),
            rs.getInt("source_revision"),
            rs.getBoolean("materialized"), rs.getBoolean("deleted"),
            Arrays.stream((Integer[]) rs.getArray("indexed_revisions").getArray()).toList(),
            rs.getLong("work_space_id"), rs.getLong("sub_class_id"),
            rs.getString("created_by"));

    private final JdbcTemplate jdbc;
    private final IdentifierGenerator ids;

    @Override
    public void save(LightTrackerItem item) {
        int affected = insert(item, """
                ON CONFLICT (local_id) DO UPDATE SET
                    revision = EXCLUDED.revision, source_local_id = EXCLUDED.source_local_id,
                    source_revision = EXCLUDED.source_revision, has_copied = EXCLUDED.has_copied, flags = EXCLUDED.flags
                WHERE air_ws_items_index.revision <= EXCLUDED.revision
                  AND air_ws_items_index.branch_id = EXCLUDED.branch_id
                  AND air_ws_items_index.workingset_id = EXCLUDED.workingset_id
                  AND air_ws_items_index.common_id = EXCLUDED.common_id
                  AND air_ws_items_index.has_copied <= EXCLUDED.has_copied
                """);
        if (affected != 1) {
            throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID, "Could not save item index " + item.localId());
        }
        saveCurrentMember(item);
    }

    @Override
    public boolean claimFamily(LightTrackerItem item) {
        int affected = insert(item, "ON CONFLICT (workingset_id, common_id) DO NOTHING");
        if (affected == 0) {
            boolean occupied = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?)
                    """, Boolean.class, item.workingSetId(), item.commonId()));
            if (!occupied) {
                throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID,
                        "Could not claim item index " + item.localId());
            }
            return false;
        }
        saveCurrentMember(item);
        return true;
    }

    private int insert(LightTrackerItem item, String onConflict) {
        long now = System.currentTimeMillis();
        String actor = item.createdBy();
        if (actor == null || actor.isBlank()) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_STATE_INVALID,
                    "LightTrackerItem write requires an actor: " + item.localId());
        }
        return jdbc.update(INSERT + onConflict,
                ids.nextId(null).longValue(), item.localId(), item.commonId(),
                item.revision(), item.sourceLocalId(), item.sourceRevision(),
                (short) (item.materialized() ? 1 : 0), item.deleted() ? 1 : 0,
                actor, now,
                item.trackerId(), item.workingSetId(),
                item.numericProductSpaceId(), item.classificationId());
    }

    private void saveCurrentMember(LightTrackerItem item) {
        LightTrackerItemRevisionEntity member = item.currentRevisionIndex();
        jdbc.update("""
                    INSERT INTO air_ws_items_revision_index
                        (id, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, revision, sub_class_id)
                    SELECT ?, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, ?, sub_class_id
                    FROM air_ws_items_index WHERE local_id = ?
                    ON CONFLICT (local_id, revision) DO NOTHING
                """, ids.nextId(null).longValue(), member.revision(), item.localId());
    }

    @Override
    public Optional<LightTrackerItem> findByIdForUpdate(Long localId) {
        if (jdbc.queryForList("SELECT local_id FROM air_ws_items_index WHERE local_id = ? FOR UPDATE",
                Long.class, localId).isEmpty()) {
            return Optional.empty();
        }
        return findById(localId);
    }

    @Override
    public Optional<LightTrackerItem> findById(Long localId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM air_ws_items_index i WHERE i.local_id = ?",
                MAPPER,
                localId).stream().findFirst();
    }

    @Override
    public void remove(Long id) {
        jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", id);
    }

    @Override
    public List<LightTrackerItem> findSourceBackedTargets(Long sourceLocalId, Integer sourceRevision) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM air_ws_items_index i "
                        + "WHERE i.source_local_id = ? AND i.source_revision = ? AND i.has_copied = 0",
                MAPPER,
                sourceLocalId,
                sourceRevision);
    }
}
