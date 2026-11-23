package com.airedgesoft.sharedkernel.workingset.infrastructure.repositories;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem.DowngradeIndexStatus;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem.EffectiveIndexStatus;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRevisionEntity;
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
                 has_copied, flags, work_space_id, master_id, parent_branch_id, sub_class_id, created_by, created_time,
                 effective_revision, flag1, flag2, flag3, flag4, lock_version,
                 effective_checked_out, effective_flags, flag5)
            SELECT ?, ?, ?, t.id, t.workingset_id, ?, ?, ?, ?, ?,
                   t.work_space, t.common_id, t.source_branch_id, t.tracker_class, ?, ?,
                   ?, ?, ?, ?, ?, 0, ?, ?, ?
            FROM air_inst_tracker_rev t
            WHERE t.id = ? AND t.workingset_id = ?
              AND t.work_space = ? AND t.tracker_class = ?
            """;

    private static final String UPDATE_CURRENT_ON_CONFLICT = """
            ON CONFLICT (local_id) DO UPDATE SET
                revision = EXCLUDED.revision, source_local_id = EXCLUDED.source_local_id,
                source_revision = EXCLUDED.source_revision, has_copied = EXCLUDED.has_copied, flags = EXCLUDED.flags
            """;

    private static final String UPDATE_EFFECTIVE_STATUS = """
            , effective_revision = EXCLUDED.effective_revision,
              effective_checked_out = EXCLUDED.effective_checked_out, effective_flags = EXCLUDED.effective_flags,
              flag1 = EXCLUDED.flag1, flag2 = EXCLUDED.flag2, flag3 = EXCLUDED.flag3,
              flag4 = EXCLUDED.flag4, flag5 = EXCLUDED.flag5
            """;

    private static final String CURRENT_INDEX_WRITE_GUARD = """
            WHERE air_ws_items_index.revision <= EXCLUDED.revision
              AND air_ws_items_index.branch_id = EXCLUDED.branch_id
              AND air_ws_items_index.workingset_id = EXCLUDED.workingset_id
              AND air_ws_items_index.common_id = EXCLUDED.common_id
              AND air_ws_items_index.has_copied <= EXCLUDED.has_copied
            """;

    private static final String COLUMNS = """
            i.local_id, i.common_id, i.branch_id, i.workingset_id, i.revision, i.source_local_id,
            i.source_revision, (i.has_copied = 1) AS materialized, (i.flags = 1) AS deleted,
            i.work_space_id, i.sub_class_id, i.created_by,
            ARRAY(SELECT r.revision FROM air_ws_items_revision_index r
                WHERE r.local_id = i.local_id ORDER BY r.revision) AS indexed_revisions,
            ARRAY(SELECT r.flags FROM air_ws_items_revision_index r
                WHERE r.local_id = i.local_id ORDER BY r.revision) AS indexed_revision_flags
            """;

    private static final RowMapper<LightTrackerItem> MAPPER = (rs, rowNum) -> LightTrackerItem.restoreRevisionState(LightTrackerItem.restore(
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
            rs.getString("created_by")),
            Arrays.asList((Integer[]) rs.getArray("indexed_revision_flags").getArray()));

    private final JdbcTemplate jdbc;
    
    @Override
    public void save(LightTrackerItem item) {
        EffectiveIndexStatus checkin = item.checkinIndexStatus();
        if (checkin != null) {
            //签入，索引版次位号跟新
            saveCheckin(item, checkin);
            return;
        }
        DowngradeIndexStatus downgrade = item.downgradeIndexStatus();
        if (downgrade != null) {
            //取消签出，索引删一个版次并降级的版次位号更新
            saveDowngrade(item, downgrade);
            return;
        }
        EffectiveIndexStatus upgrade = item.upgradeIndexStatus();
        if (upgrade != null) {
            //签出，索引加一个版次并升级版次位号更新
            saveUpgrade(item, upgrade);
            return;
        }
        saveWithCurrentMember(item, UPDATE_CURRENT_ON_CONFLICT + CURRENT_INDEX_WRITE_GUARD, null);
    }

    private void saveUpgrade(LightTrackerItem item, EffectiveIndexStatus status) {
        saveWithCurrentMember(item, UPDATE_CURRENT_ON_CONFLICT + UPDATE_EFFECTIVE_STATUS + CURRENT_INDEX_WRITE_GUARD,
                status);
    }

    private void saveWithCurrentMember(LightTrackerItem item, String onConflict, EffectiveIndexStatus upgrade) {
        int affected = insert(item, onConflict, upgrade);
        if (affected != 1) {
            throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID, "Could not save item index " + item.localId());
        }
        saveCurrentMember(item);
    }

    @Override
    public boolean claimFamily(LightTrackerItem item) {
        int affected = insert(item, "ON CONFLICT (workingset_id, common_id) DO NOTHING", item.upgradeIndexStatus());
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

    private int insert(LightTrackerItem item, String onConflict, EffectiveIndexStatus upgrade) {
        long now = System.currentTimeMillis();
        String actor = item.createdBy();
        if (actor == null || actor.isBlank()) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_STATE_INVALID,
                    "LightTrackerItem write requires an actor: " + item.localId());
        }
        return jdbc.update(INSERT + onConflict,
                Ids.nextId(),
                item.localId(),
                item.commonId(),
                item.revision(),
                item.sourceLocalId(),
                item.sourceRevision(),
                item.materialized() ? 1 : 0,
                item.deleted() ? 1 : 0,
                actor,
                now,
                upgrade == null ? 1 : upgrade.effectiveRevision(),
                upgrade == null ? item.initialIndexStatus().flag1() : upgrade.flag1(),
                upgrade == null ? item.initialIndexStatus().flag2() : upgrade.flag2(),
                upgrade == null ? item.initialIndexStatus().flag3() : upgrade.flag3(),
                upgrade == null ? item.initialIndexStatus().flag4() : upgrade.flag4(),
                upgrade == null ? item.initialIndexStatus().effectiveCheckedOut() : upgrade.effectiveCheckedOut(),
                upgrade == null ? item.initialIndexStatus().effectiveFlags() : upgrade.effectiveFlags(),
                upgrade == null ? item.initialIndexStatus().flag5() : upgrade.flag5(),
                item.trackerId(),
                item.workingSetId(),
                item.productSpaceId(),
                item.classificationId()
        );
    }

    /**
     * 签入索引更新
     * @param item 索引
     * @param status 索引签入版次位号信息
     */
    private void saveCheckin(LightTrackerItem item, EffectiveIndexStatus status) {
        int updated = jdbc.update("""
                UPDATE air_ws_items_index SET effective_revision = ?, effective_checked_out = ?, effective_flags = ?,
                    flag1 = ?, flag2 = ?, flag3 = ?, flag4 = ?, flag5 = ?
                WHERE local_id = ? AND revision = ?
                """, status.effectiveRevision(), status.effectiveCheckedOut(), status.effectiveFlags(),
                status.flag1(), status.flag2(), status.flag3(), status.flag4(), status.flag5(),
                item.localId(), item.revision());
        if (updated != 1) {
            throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID,
                    "Could not check in item index " + item.localId());
        }
    }

    /**
     * 索引降级
     * @param item 索引
     * @param status 索引降级版次位号信息
     */
    private void saveDowngrade(LightTrackerItem item, DowngradeIndexStatus status) {
        int updated = jdbc.update("""
                UPDATE air_ws_items_index SET revision = ?, source_local_id = ?, source_revision = ?, has_copied = ?,
                    effective_revision = ?, effective_checked_out = ?, effective_flags = ?,
                    flag1 = ?, flag2 = ?, flag3 = ?, flag4 = ?, flag5 = ?
                WHERE local_id = ? AND revision = ?
                """, item.revision(),
                item.sourceLocalId(),
                item.sourceRevision(),
                item.materialized() ? 1 : 0,
                status.effectiveRevision(),
                status.effectiveCheckedOut(),
                status.effectiveFlags(),
                status.flag1(),
                status.flag2(),
                status.flag3(),
                status.flag4(),
                status.flag5(),
                item.localId(),
                status.removedRevision());
        if (updated != 1 || jdbc.update("""
                DELETE FROM air_ws_items_revision_index WHERE local_id = ? AND revision = ?
                """, item.localId(), status.removedRevision()) != 1) {
            throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID,
                    "Could not roll back item index " + item.localId());
        }
    }

    private void saveCurrentMember(LightTrackerItem item) {
        LightTrackerItemRevisionEntity member = item.currentRevisionIndex();
        jdbc.update("""
                    INSERT INTO air_ws_items_revision_index
                        (id, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, revision,
                         sub_class_id, flags, lock_version, created_by, created_time)
                    SELECT ?, i.work_space_id, i.workingset_id, i.master_id, i.branch_id, i.common_id, i.local_id, ?,
                           i.sub_class_id, ?, 0, ?, ?
                    FROM air_ws_items_index i WHERE i.local_id = ?
                    ON CONFLICT (local_id, revision) DO NOTHING
                """, Ids.nextId(), member.revision(), member.flags(), item.createdBy(),
                System.currentTimeMillis(), item.localId());
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
