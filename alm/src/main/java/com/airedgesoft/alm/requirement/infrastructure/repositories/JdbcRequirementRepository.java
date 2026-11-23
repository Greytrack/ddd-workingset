package com.airedgesoft.alm.requirement.infrastructure.repositories;


import com.airedgesoft.alm.requirement.application.port.RequirementMutationStore;
import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.sharedkernel.RequestThreadHolder;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Requirement 的 PostgreSQL/Spring JDBC 仓储。
 *
 * <p>写入使用 {@code lock_version} 条件更新：只有当前数据库版本等于待写入版本减一才更新；
 * 0 行受影响视为并发冲突，显式抛出 {@link LockVersionConflictException}，不静默覆盖。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcRequirementRepository implements RequirementMutationStore {

    private static final String INSERT = """
            INSERT INTO air_inst_requirement_rev
                (id, instance_id, common_id, tracker, name, description, revision, version, lock_version,
                 delete_flag, workingset_id, work_space, type, owner, created_by, created_time, updated_by, updated_time,
                 flags, checked_out)
            SELECT ?, ?, ?, t.id, ?, ?, ?, ?, ?, ?, t.workingset_id, t.work_space, t.tracker_class, t.owner,
                   ?, ?, ?, ?, ?, ?
            FROM air_inst_tracker_rev t
            WHERE t.id = ? AND t.delete_flag = 0
            """;

    private static final String UPDATE = """
            UPDATE air_inst_requirement_rev
            SET name = ?, description = ?, version = ?, delete_flag = ?, lock_version = ?, flags = ?, checked_out = ?, updated_by = ?, updated_time = ?
            WHERE instance_id = ? AND revision = ? AND lock_version = ?
            """;

    private static final RowMapper<Requirement> MAPPER = (rs, rowNum) -> Requirement.restore(
            rs.getLong("instance_id"),
            rs.getLong("common_id"),
            rs.getLong("tracker"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("revision"),
            rs.getString("version"),
            rs.getLong("lock_version"), true, rs.getInt("delete_flag") == 1,
            rs.getString("created_by"), rs.getString("flags"), rs.getInt("checked_out") == 1);

    private final JdbcTemplate jdbc;
    private final JdbcCommonInstanceStore commonInstances;
    private final IdentifierGenerator ids;


    @Override
    public void save(Requirement requirement) {
        long lockVersion = requirement.lockVersion();
        if (lockVersion == 0) {
            insertRevision(requirement);
        } else {
            long expected = lockVersion - 1;
            Requirement current = lockById(requirement.localId())
                    .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "Requirement no longer exists"));
            if (current.lockVersion() != expected) {
                throw new LockVersionConflictException(expected, current.lockVersion());
            }
            if (requirement.revision() == (long) current.revision() + 1) {
                insertRevision(requirement);
                jdbc.update("""
                        UPDATE air_inst_requirement_rev SET flags = overlay(flags placing '0' from 3 for 1)
                        WHERE instance_id = ? AND revision < ? AND substring(flags from 3 for 1) = '1'
                        """, requirement.localId(), requirement.revision());
            } else if (!requirement.revision().equals(current.revision()) || jdbc.update(UPDATE,
                    requirement.title(), requirement.description(), requirement.version(), requirement.deleted() ? 1 : 0,
                    lockVersion, requirement.flags(), requirement.checkedOut() ? 1 : 0,
                    RequestThreadHolder.getUserId(), System.currentTimeMillis(),
                    requirement.localId(), requirement.revision(), expected) != 1) {
                throw new LockVersionConflictException(expected, current.lockVersion());
            }
            if (current.checkedOut() && !requirement.checkedOut()) {
                jdbc.update("""
                        UPDATE air_inst_requirement_rev SET flags = '000' || substring(flags from 4)
                        WHERE instance_id = ? AND revision < ? AND substring(flags from 1 for 3) <> '000'
                        """, requirement.localId(), requirement.revision());
                jdbc.update("""
                        UPDATE air_inst_requirement_rev
                        SET checkin_user = ?, checkin_time = ?, checked_out = 0
                        WHERE instance_id = ? AND revision = ?
                        """, RequestThreadHolder.getLongUserId(), System.currentTimeMillis(),
                        requirement.localId(), requirement.revision());
            }
        }
        saveCommonInstance(requirement.localId());
    }

    private void insertRevision(Requirement requirement) {
        String actor = requirement.createdBy();
        if (actor == null || actor.isBlank()) {
            throw new DomainException(WorkingsetResultCode.APPLICATION_DENIED);
        }
        long now = System.currentTimeMillis();
        int inserted = jdbc.update(INSERT, ids.nextId(null).longValue(), requirement.localId(), requirement.commonId(),
                requirement.title(), requirement.description(), requirement.revision(), requirement.version(),
                requirement.lockVersion(), requirement.deleted() ? 1 : 0,
                actor, now, actor, now, requirement.flags(), requirement.checkedOut() ? 1 : 0, requirement.trackerId());
        if (inserted != 1) {
            throw DomainException.withMessage(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT,
                    "Tracker " + requirement.trackerId() + " is unavailable");
        }
        if (requirement.checkedOut()) {
            jdbc.update("""
                    UPDATE air_inst_requirement_rev
                    SET checkout_user = ?, checkout_time = ?, checked_out = 1
                    WHERE instance_id = ? AND revision = ?
                    """, RequestThreadHolder.getLongUserId(), now, requirement.localId(), requirement.revision());
        }
    }

    @Override
    public Optional<Requirement> findById(Long localId) {
        return jdbc.query(
                """
                SELECT instance_id, common_id, tracker, name, description, revision, version, lock_version,
                       delete_flag, created_by, flags, checked_out
                FROM air_inst_requirement_rev WHERE instance_id = ?
                ORDER BY revision DESC LIMIT 1
                """,
                MAPPER,
                localId).stream().findFirst();
    }

    @Override
    public Optional<Requirement> lockById(Long id) {
        // The first surviving row is the stable lock for this instance across checkouts and cancellations.
        jdbc.queryForList("""
                SELECT id FROM air_inst_requirement_rev WHERE instance_id = ?
                ORDER BY revision LIMIT 1 FOR UPDATE
                """, Long.class, id);
        return findById(id);
    }

    @Override
    public Optional<Requirement> lockFamilyInWorkingSet(Long trackerId, Long commonId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.instance_id FROM air_inst_requirement_rev r
                WHERE r.workingset_id = (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ? AND delete_flag = 0)
                  AND r.common_id = ?
                """, Long.class, trackerId, commonId).stream().findFirst().flatMap(this::lockById);
    }

    @Override
    public Requirement cancelCheckout(Requirement current) {
        Long instanceId = current.localId();
        Requirement latest = lockById(instanceId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "Requirement no longer exists"));
        if (!latest.lockVersion().equals(current.lockVersion()) || !latest.revision().equals(current.revision())) {
            throw new LockVersionConflictException(current.lockVersion(), latest.lockVersion());
        }
        int previousRevision = current.revision() - 1;
        if (jdbc.update("""
                UPDATE air_inst_requirement_rev SET lock_version = ?, checked_out = 0, flags = overlay(flags placing '1' from 3 for 1), updated_by = ?, updated_time = ?
                WHERE instance_id = ? AND revision = ?
                """, Math.incrementExact(current.lockVersion()), RequestThreadHolder.getUserId(), System.currentTimeMillis(), instanceId, previousRevision) != 1
                || jdbc.update("""
                DELETE FROM air_inst_requirement_rev WHERE instance_id = ? AND revision = ? AND lock_version = ?
                """, instanceId, current.revision(), current.lockVersion()) != 1) {
            throw new DomainException(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT);
        }
        saveCommonInstance(instanceId);
        return findById(instanceId).orElseThrow(() -> new DomainException(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT));
    }

    @Override
    public void delete(Requirement requirement) {
        long id = requirement.localId();
        jdbc.update("""
                DELETE FROM event_publication
                WHERE event_type = ? AND serialized_event::jsonb ->> 'sourceLocalId' = ?
                """, BusinessObjectUpdateEvent.class.getName(), Long.toString(id));
        if (jdbc.update("DELETE FROM air_inst_requirement_rev WHERE instance_id = ?", id) > 0) {
            commonInstances.remove(id);
        }
    }

    private void saveCommonInstance(long id) {
        AirInstCommonPo common = jdbc.queryForObject("""
                SELECT t.work_space, r.number, r.name, r.version, r.revision,
                       r.delete_flag, r.lock_version, r.common_id, r.workingset_id, r.tracker
                FROM air_inst_requirement_rev r JOIN air_inst_tracker_rev t ON t.id = r.tracker
                WHERE r.instance_id = ?
                ORDER BY r.revision DESC LIMIT 1
                """, (rs, row) -> new AirInstCommonPo(id, 1401L, 140101L, 14010101L,
                rs.getObject("work_space", Long.class), rs.getString("number"), rs.getString("name"),
                null, rs.getString("version"), rs.getLong("revision"), rs.getInt("delete_flag"),
                rs.getLong("lock_version"), null, null, null, null, null, null, rs.getLong("common_id"),
                rs.getLong("workingset_id"), null, rs.getLong("tracker"), rs.getLong("revision")), id);
        commonInstances.save(common);
    }

}
