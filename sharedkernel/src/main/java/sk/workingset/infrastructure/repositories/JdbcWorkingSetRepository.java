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
    public Optional<WorkingSet> findWorkingSet(WorkingSetId id) {
        return loadReadyWorkingSet(id, false);
    }

    @Override
    public Optional<WorkingSet> lockWorkingSet(WorkingSetId id) {
        return loadReadyWorkingSet(id, true);
    }

    private Optional<WorkingSet> loadReadyWorkingSet(WorkingSetId id, boolean lock) {
        requireReady(id);
        return loadWorkingSet(id, lock);
    }

    @Override
    public Optional<WorkingSet> lockWorkingSetForCreation(WorkingSetId id) {
        return loadWorkingSet(id, true);
    }

    @Override
    public Optional<WorkingSet> findWorkingSetForCreation(WorkingSetId id) {
        return loadWorkingSet(id, false);
    }

    private Optional<WorkingSet> loadWorkingSet(WorkingSetId id, boolean lock) {
        String sql = "SELECT NULLIF(parent_id, 0), name, COALESCE(color, '#FFFF')"
                + " FROM air_inst_workingset WHERE id = ? AND delete_flag = 0";
        if (lock) {
            sql += " FOR UPDATE";
        }
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
    public boolean save(WorkingSet workingSet, Long actor) {
        if (workingSet.isNew() && !insert(workingSet, actor)) {
            return false;
        }
        updateMembership(workingSet);
        return true;
    }

    private boolean insert(WorkingSet workingSet, Long actor) {
        long id = workingSet.id().numericValue();
        Long owner = actor == null ? RequestThreadHolder.getLongUserId() : actor;
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
        if (inserted) {
            commonInstances.save(new AirInstCommonPo(id, 1008L, 100801L, 10080101L, null,
                    "WS-" + id, workingSet.name(), null, null, 1L, 0, 0L, auditActor, now, auditActor, now,
                    null, owner, id, id, workingSet.parentWorkingSetId().numericValue(), 584190236948832L, 1L));
        }
        return inserted;
    }

    @Override
    public List<WorkingSetId> findChildren(WorkingSetId id) {
        return jdbc.queryForList("""
                SELECT w.id FROM air_inst_workingset w
                WHERE w.parent_id = ? AND w.delete_flag = 0 AND NOT EXISTS (
                    SELECT 1 FROM working_set_creations c WHERE c.new_working_set_id = w.id
                    AND c.status IN ('Pending', 'Running')
                    AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
                ORDER BY w.id
                """, Long.class, id.numericValue()).stream().map(WorkingSetId::of).toList();
    }

    @Override
    public List<WorkingSetId> findAncestors(WorkingSetId id) {
        List<Long> ancestors = jdbc.queryForList("""
                WITH RECURSIVE lineage(working_set_id, parent_working_set_id, distance) AS (
                    SELECT id, NULLIF(parent_id, 0), 0
                    FROM air_inst_workingset WHERE id = ? AND delete_flag = 0
                    UNION ALL
                    SELECT parent.id, NULLIF(parent.parent_id, 0), child.distance + 1
                    FROM air_inst_workingset parent JOIN lineage child ON parent.id = child.parent_working_set_id
                    WHERE parent.delete_flag = 0
                ) CYCLE working_set_id SET cyclic USING path
                SELECT working_set_id FROM lineage
                WHERE distance > 0 AND NOT EXISTS (
                    SELECT 1 FROM working_set_creations c
                    WHERE c.new_working_set_id = lineage.working_set_id
                    AND c.status IN ('Pending', 'Running')
                    AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
                ORDER BY distance
                """, Long.class, id.numericValue());
        if (!ancestors.isEmpty() && !WorkingSet.DEFAULT_ID.numericValue().equals(ancestors.getLast())) {
            throw new BindingFailure(Reason.WorkingSetCycle, id.value());
        }
        return ancestors.stream().map(WorkingSetId::of).toList();
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
    public void deleteBindingsToTracker(TrackerId trackerId) {
        jdbc.update("DELETE FROM air_ws_workingset_branch_relation WHERE tracker_id = ?", trackerId.numericValue());
    }

    @Override
    public List<WorkingSetId> findWorkingSetsBoundToSpace(String spaceId) {
        return jdbc.query("SELECT working_set_id FROM space_bindings WHERE product_space_id = ? ORDER BY working_set_id",
                (rs, row) -> WorkingSetId.of(rs.getLong(1)), Long.valueOf(spaceId));
    }

    @Override
    @Transactional
    public void deleteWorkingSet(WorkingSetId id) {
        if (jdbc.update("DELETE FROM air_inst_workingset WHERE id = ? AND delete_flag = 0", id.numericValue()) == 1) {
            commonInstances.remove(id.numericValue());
        }
    }

}
