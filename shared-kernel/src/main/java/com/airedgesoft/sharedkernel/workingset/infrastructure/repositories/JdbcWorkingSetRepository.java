package com.airedgesoft.sharedkernel.workingset.infrastructure.repositories;

import com.airedgesoft.lib.restful.etag.EtagContext;
import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * WorkingSet 的 JDBC 仓储实现；谱系查询带防环与根/非根约束。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcWorkingSetRepository implements WorkingSetRepository {
    private final JdbcTemplate jdbc;
        private final JdbcCommonInstanceStore commonInstances;

    private static String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new DomainException(WorkingsetResultCode.APPLICATION_DENIED);
        }
        return actor;
    }

    /**
     * Full physical row for internal compatibility reads, including soft-deleted rows.
     */
    public Optional<WorkingSetRow> findLegacyRow(Long id) {
        return jdbc.query("SELECT * FROM air_inst_workingset WHERE id = ?",
                (rs, row) -> WorkingSetRow.read(rs), id).stream().findFirst();
    }

    @Override
    public Optional<WorkingSet> findById(Long id) {
        requireReady(id);
        return loadWorkingSet(id);
    }

    @Override
    public Optional<WorkingSet> findByIdForUpdate(Long id) {
        requireReady(id);
        return loadLocked(id, " FOR UPDATE");
    }

    @Override
    public Optional<WorkingSet> findByIdForChildCreation(Long id) {
        requireReady(id);
        return loadLocked(id, " FOR KEY SHARE");
    }

    private Optional<WorkingSet> loadLocked(Long id, String lockingClause) {
        return jdbc.query("SELECT id FROM air_inst_workingset WHERE id = ? AND delete_flag = 0" + lockingClause,
                (row, index) -> row.getLong(1), id).stream().findFirst().flatMap(this::loadWorkingSet);
    }

    @Override
    public Optional<WorkingSet> findByIdForCreation(Long id) {
        return loadWorkingSet(id);
    }

    @Override
    public Optional<WorkingSet> findByIdForTaskWrite(Long id) {
        return loadLocked(id, " FOR KEY SHARE");
    }

    private Optional<WorkingSet> loadWorkingSet(Long id) {
        String sql = "SELECT NULLIF(parent_id, 0), name, COALESCE(color, '#FFFF'), owner, updated_by, lock_version"
                + " FROM air_inst_workingset WHERE id = ? AND delete_flag = 0";
        List<Object[]> rows = jdbc.query(sql,
                (rs, row) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, Long.class), rs.getString(5), rs.getObject(6, Long.class)},
                id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<WorkingSet.SpaceBinding> spaces = jdbc.query("""
                SELECT sb.product_space_id, ps.name AS product_space_name, sb.origin, sb.state
                FROM space_bindings sb
                LEFT JOIN air_inst_product_space ps ON ps.id = sb.product_space_id
                WHERE sb.working_set_id = ? ORDER BY sb.product_space_id
                """, (rs, row) -> new WorkingSet.SpaceBinding(id, rs.getLong("product_space_id"),
                rs.getString("product_space_name"),
                WorkingSet.SpaceOrigin.valueOf(rs.getString("origin")),
                WorkingSet.SpaceState.valueOf(rs.getString("state"))), id);
        List<WorkingSet.TrackerBinding> trackers = jdbc.query("""
                SELECT tracker_id, product_space_id, classification_id, sharing_mode = 'SHARED' AS shared, origin, shown FROM tracker_bindings
                WHERE working_set_id = ? ORDER BY product_space_id, classification_id
                """, (rs, row) -> new WorkingSet.TrackerBinding(id, rs.getLong("tracker_id"),
                rs.getLong("product_space_id"), rs.getLong("classification_id"),
                rs.getBoolean("shared"),
                WorkingSet.TrackerOrigin.valueOf(rs.getString("origin")), rs.getBoolean("shown")), id);
        Object[] header = rows.get(0);
        String parent = (String) header[0];
        String name = (String) header[1];
        WorkingSet workingSet = new WorkingSet(id, parent == null ? null : Long.valueOf(parent), name,
                (String) header[2], spaces, trackers, (Long) header[3], (String) header[4]);
        EtagContext.recordLoaded("workingsets/" + id, workingSet, (Long) header[5]);
        return Optional.of(workingSet);
    }

    private void requireReady(Long id) {
        boolean preparing = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM air_ws_creations
                    WHERE new_working_set_id = ? AND status IN ('Pending', 'Running')
                      AND COALESCE(result->'request'->>'mode', 'CREATE') = 'CREATE')
                """, Boolean.class, id));
        if (preparing) {
            throw new DomainException(FailureReason.PREPARING, Long.toString(id));
        }
    }

    @Override
    public void save(WorkingSet workingSet) {
        if (workingSet.isNew()) {
            insert(workingSet);
        } else {
            updateHeader(workingSet);
        }
        if (!workingSet.deleted()) {
            updateMembership(workingSet);
        }
    }

    private void updateHeader(WorkingSet workingSet) {
        Long id = workingSet.id();
        String actor = requireActor(workingSet.actor());
        long now = System.currentTimeMillis();
        int deleteFlag = workingSet.deleted() ? 1 : 0;
        List<Long> versions = jdbc.query("""
                UPDATE air_inst_workingset
                SET name = ?, color = ?, delete_flag = ?, lock_version = COALESCE(lock_version, 0) + 1,
                    updated_by = ?, updated_time = ?
                WHERE id = ? AND delete_flag = 0
                RETURNING lock_version
                """, (rs, row) -> rs.getLong(1), workingSet.name(), workingSet.color(), deleteFlag, actor, now, id);
        if (versions.isEmpty()) {
            throw new DomainException(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id));
        }
        EtagContext.recordSaved("workingsets/" + id, workingSet, versions.get(0));
        jdbc.update("""
                UPDATE air_inst_common
                SET name = ?, delete_flag = ?, updated_by = ?, updated_time = ?
                WHERE id = ?
                """, workingSet.name(), deleteFlag, actor, now, id);
    }

    private void insert(WorkingSet workingSet) {
        long id = workingSet.id();
        Long owner = workingSet.owner();
        if (owner == null || owner <= 0) {
            throw new DomainException(WorkingsetResultCode.APPLICATION_DENIED);
        }
        String auditActor = requireActor(workingSet.actor());
        long now = System.currentTimeMillis();
        boolean inserted = jdbc.update("""
                        INSERT INTO air_inst_workingset (id, instance_id, common_id, workingset_id, parent_id,
                            base_class, class, type, tracker, name, number, color, owner, revision, version_type,
                            delete_flag, lock_version, created_by, created_time, updated_by, updated_time)
                        VALUES (?, ?, ?, ?, ?, 1008, 100801, 10080101, 584190236948832, ?, ?, ?, ?, 1, 10001,
                            0, 0, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                        """, id, id, id, id, workingSet.parentWorkingSetId(), workingSet.name(), "WS-" + id,
                workingSet.color(), owner, auditActor, now, auditActor, now) == 1;
        if (!inserted) {
            throw new DomainException(BindingResCode.WORKING_SET_ALREADY_EXISTS, Long.toString(workingSet.id()));
        }
        commonInstances.save(new AirInstCommonPo(id, 1008L, 100801L, 10080101L, null,
                "WS-" + id, workingSet.name(), null, null, 1L, 0, 0L, auditActor, now, auditActor, now,
                null, owner, id, id, workingSet.parentWorkingSetId(), 584190236948832L, 1L));
    }

    private void updateMembership(WorkingSet workingSet) {
        String actor = requireActor(workingSet.actor());
        workingSet.removedTrackerBindings().forEach(trackerId -> deleteTrackerBinding(workingSet.id(), trackerId));
        workingSet.addedTrackerBindings().forEach(binding -> insertTrackerBinding(actor, binding));
        workingSet.changedTrackerVisibility().forEach(binding -> updateTrackerBinding(actor, binding));
    }

    private void insertTrackerBinding(String actor, WorkingSet.TrackerBinding binding) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                        INSERT INTO air_ws_workingset_branch_relation (id, workingset_id, work_space, tracker_id,
                            lock_version, created_by, created_time, updated_by, updated_time, is_show)
                        VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                        """, Ids.nextId(), binding.workingSetId(),
                binding.numericProductSpaceId(), binding.trackerId(), actor, now, actor, now,
                binding.shown() ? 1 : 0);
    }

    private void updateTrackerBinding(String actor, WorkingSet.TrackerBinding binding) {
        int shown = binding.shown() ? 1 : 0;
        jdbc.update("""
                        UPDATE air_ws_workingset_branch_relation
                        SET is_show = ?, lock_version = COALESCE(lock_version, 0) + 1, updated_by = ?, updated_time = ?
                        WHERE workingset_id = ? AND tracker_id = ? AND work_space IS NOT NULL
                          AND COALESCE(is_show, 1) <> ?
                        """, shown, actor, System.currentTimeMillis(),
                binding.workingSetId(), binding.trackerId(), shown);
    }

    private void deleteTrackerBinding(Long id, Long trackerId) {
        jdbc.update("DELETE FROM air_ws_workingset_branch_relation WHERE workingset_id = ? AND tracker_id = ? AND work_space IS NOT NULL",
                id, trackerId);
    }

    @Override
    public void delete(WorkingSet workingSet) {
        Long id = workingSet.id();
        if (jdbc.update("DELETE FROM air_inst_workingset WHERE id = ? AND delete_flag = 0", id) == 1) {
            commonInstances.remove(id);
        }
    }

}
