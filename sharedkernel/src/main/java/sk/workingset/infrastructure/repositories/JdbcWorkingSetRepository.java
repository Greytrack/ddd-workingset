package sk.workingset.infrastructure.repositories;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import sk.RequestThreadHolder;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.*;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.repository.WorkingSetRepository;

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
    private final IdentifierGenerator ids;
    private final JdbcCommonInstanceStore commonInstances;

    @Override
    public Optional<WorkingSet> findById(WorkingSetId id) {
        requireReady(id);
        return loadWorkingSet(id);
    }

    @Override
    public Optional<WorkingSet> findByIdForCreation(WorkingSetId id) {
        return loadWorkingSet(id);
    }

    private Optional<WorkingSet> loadWorkingSet(WorkingSetId id) {
        String sql = "SELECT NULLIF(parent_id, 0), name, COALESCE(color, '#FFFF')"
                + " FROM air_inst_workingset WHERE id = ? AND delete_flag = 0";
        List<String[]> rows = jdbc.query(sql,
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)},
                id.numericValue());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<WorkingSet.SpaceBinding> spaces = jdbc.query("""
                SELECT sb.product_space_id, ps.name AS product_space_name, sb.origin, sb.state
                FROM space_bindings sb
                LEFT JOIN product_spaces ps ON ps.product_space_id = sb.product_space_id
                WHERE sb.working_set_id = ? ORDER BY sb.product_space_id
                """, (rs, row) -> new WorkingSet.SpaceBinding(id, rs.getLong("product_space_id"),
                rs.getString("product_space_name"),
                WorkingSet.SpaceOrigin.valueOf(rs.getString("origin")),
                WorkingSet.SpaceState.valueOf(rs.getString("state"))), id.numericValue());
        List<WorkingSet.TrackerBinding> trackers = jdbc.query("""
                SELECT tracker_id, product_space_id, classification_id, sharing_mode, origin, shown FROM tracker_bindings
                WHERE working_set_id = ? ORDER BY product_space_id, classification_id
                """, (rs, row) -> new WorkingSet.TrackerBinding(id, TrackerId.of(rs.getLong("tracker_id")),
                rs.getLong("product_space_id"), rs.getLong("classification_id"),
                SharingMode.valueOf(rs.getString("sharing_mode")),
                WorkingSet.TrackerOrigin.valueOf(rs.getString("origin")), rs.getBoolean("shown")), id.numericValue());
        String parent = rows.getFirst()[0];
        String name = rows.getFirst()[1];
        return Optional.of(new WorkingSet(id, parent == null ? null : WorkingSetId.of(parent), name,
                rows.getFirst()[2], spaces, trackers));
    }

    @Override
    public void requireReady(WorkingSetId id) {
        boolean preparing = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM working_set_creations
                    WHERE new_working_set_id = ? AND status IN ('Pending', 'Running')
                      AND COALESCE(result->'request'->>'mode', 'CREATE') = 'CREATE')
                """, Boolean.class, id.numericValue()));
        if (preparing) {
            throw DomainException.withMessage(FailureReason.PREPARING, id.value());
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(WorkingSet workingSet) {
        if (workingSet.isNew()) {
            insert(workingSet);
        } else {
            updateHeader(workingSet);
        }
        updateMembership(workingSet);
    }

    private void updateHeader(WorkingSet workingSet) {
        WorkingSetId id = workingSet.id();
        String actor = RequestThreadHolder.getUserId();
        long now = System.currentTimeMillis();
        int updated = jdbc.update("""
                        UPDATE air_inst_workingset
                        SET name = ?, color = ?, lock_version = COALESCE(lock_version, 0) + 1,
                            updated_by = ?, updated_time = ?
                        WHERE id = ? AND delete_flag = 0
                        """, workingSet.name(), workingSet.color(), actor, now, id.numericValue());
        if (updated != 1) {
            throw new BindingFailure(Reason.WorkingSetNotFound, id.value());
        }
        jdbc.update("""
                UPDATE air_inst_common
                SET name = ?, updated_by = ?, updated_time = ?
                WHERE id = ?
                """, workingSet.name(), actor, now, id.numericValue());
    }

    private void insert(WorkingSet workingSet) {
        long id = workingSet.id().numericValue();
        Long owner = RequestThreadHolder.getLongUserId();
        if (owner == null || owner <= 0) {
            throw new DomainException(sk.workingset.error.WorkingsetResultCode.APPLICATION_DENIED);
        }
        String auditActor = owner.toString();
        long now = System.currentTimeMillis();
        boolean inserted = jdbc.update("""
                        INSERT INTO air_inst_workingset (id, instance_id, common_id, workingset_id, parent_id,
                            base_class, class, type, tracker, name, number, color, owner, revision, version_type,
                            delete_flag, lock_version, created_by, created_time, updated_by, updated_time)
                        VALUES (?, ?, ?, ?, ?, 1008, 100801, 10080101, 584190236948832, ?, ?, ?, ?, 1, 10001,
                            0, 0, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                        """, id, id, id, id, workingSet.parentWorkingSetId().numericValue(), workingSet.name(), "WS-" + id,
                workingSet.color(), owner, auditActor, now, auditActor, now) == 1;
        if (!inserted) {
            throw new BindingFailure(Reason.WorkingSetAlreadyExists, workingSet.id().value());
        }
        commonInstances.save(new AirInstCommonPo(id, 1008L, 100801L, 10080101L, null,
                "WS-" + id, workingSet.name(), null, null, 1L, 0, 0L, auditActor, now, auditActor, now,
                null, owner, id, id, workingSet.parentWorkingSetId().numericValue(), 584190236948832L, 1L));
    }

    private void updateMembership(WorkingSet workingSet) {
        workingSet.removedTrackerBindings().forEach(trackerId -> deleteTrackerBinding(workingSet.id(), trackerId));
        workingSet.addedTrackerBindings().forEach(this::insertTrackerBinding);
        workingSet.changedTrackerVisibility().forEach(this::updateTrackerBinding);
    }

    private void insertTrackerBinding(WorkingSet.TrackerBinding binding) {
        String actor = RequestThreadHolder.getUserId();
        long now = System.currentTimeMillis();
        jdbc.update("""
                        INSERT INTO air_ws_workingset_branch_relation (id, workingset_id, work_space, tracker_id,
                            lock_version, created_by, created_time, updated_by, updated_time, is_show)
                        VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                        """, ids.nextId(null).longValue(), binding.workingSetId().numericValue(),
                binding.numericProductSpaceId(), binding.trackerId().numericValue(), actor, now, actor, now,
                binding.shown() ? 1 : 0);
    }

    private void updateTrackerBinding(WorkingSet.TrackerBinding binding) {
        int shown = binding.shown() ? 1 : 0;
        jdbc.update("""
                        UPDATE air_ws_workingset_branch_relation
                        SET is_show = ?, lock_version = COALESCE(lock_version, 0) + 1, updated_by = ?, updated_time = ?
                        WHERE workingset_id = ? AND tracker_id = ? AND work_space IS NOT NULL
                          AND COALESCE(is_show, 1) <> ?
                        """, shown, RequestThreadHolder.getUserId(), System.currentTimeMillis(),
                binding.workingSetId().numericValue(), binding.trackerId().numericValue(), shown);
    }

    private void deleteTrackerBinding(WorkingSetId id, TrackerId trackerId) {
        jdbc.update("DELETE FROM air_ws_workingset_branch_relation WHERE workingset_id = ? AND tracker_id = ? AND work_space IS NOT NULL",
                id.numericValue(), trackerId.numericValue());
    }

    @Override
    @Transactional
    public void delete(WorkingSet workingSet) {
        WorkingSetId id = workingSet.id();
        if (jdbc.update("DELETE FROM air_inst_workingset WHERE id = ? AND delete_flag = 0", id.numericValue()) == 1) {
            commonInstances.remove(id.numericValue());
        }
    }

}
