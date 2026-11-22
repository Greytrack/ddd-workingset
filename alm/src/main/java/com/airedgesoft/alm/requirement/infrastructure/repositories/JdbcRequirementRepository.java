package com.airedgesoft.alm.requirement.infrastructure.repositories;


import com.airedgesoft.alm.requirement.application.port.RequirementMutationStore;
import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
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
            INSERT INTO requirements
                (local_id, common_id, tracker_id, name, title, description, revision, version, lock_version,
                 delete_flag, working_set_id, created_by, created_time)
            SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, t.workingset_id, ?, ?
            FROM air_inst_tracker_rev t
            WHERE t.id = ?
            """;

    private static final String UPDATE = """
            UPDATE requirements
            SET name = ?, title = ?, description = ?, revision = ?, version = ?, delete_flag = ?, lock_version = ?
            WHERE local_id = ? AND lock_version = ?
            """;

    private static final RowMapper<Requirement> MAPPER = (rs, rowNum) -> Requirement.restore(
            rs.getLong("local_id"),
            rs.getLong("common_id"),
            rs.getLong("tracker_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getInt("revision"),
            rs.getString("version"),
            rs.getLong("lock_version"), true, rs.getBoolean("delete_flag"),
            rs.getString("created_by"));

    private final JdbcTemplate jdbc;
    private final JdbcCommonInstanceStore commonInstances;


    @Override
    public void save(Requirement requirement) {
        long lockVersion = requirement.lockVersion();
        if (lockVersion == 0) {
            String actor = requirement.createdBy();
            if (actor == null || actor.isBlank()) {
                throw new DomainException(WorkingsetResultCode.APPLICATION_DENIED);
            }
            jdbc.update(INSERT, requirement.localId(), requirement.commonId(),
                    requirement.trackerId(), requirement.title(), requirement.title(), requirement.description(),
                    requirement.revision(), requirement.version(), lockVersion, requirement.deleted(),
                    actor, System.currentTimeMillis(),
                    requirement.trackerId());
            saveCommonInstance(requirement.localId());
            return;
        }
        long expected = lockVersion - 1;
        int affected = jdbc.update(UPDATE, requirement.title(), requirement.title(), requirement.description(),
                requirement.revision(), requirement.version(), requirement.deleted(), lockVersion,
                requirement.localId(), expected);
        if (affected == 0) {
            throw new LockVersionConflictException(expected, currentLockVersion(requirement.localId()));
        }
        saveCommonInstance(requirement.localId());
    }

    @Override
    public Optional<Requirement> findById(Long localId) {
        return jdbc.query(
                "SELECT local_id, common_id, tracker_id, title, description, revision, version, lock_version, delete_flag, created_by "
                        + "FROM requirements WHERE local_id = ?",
                MAPPER,
                localId).stream().findFirst();
    }

    @Override
    public Optional<Requirement> lockById(Long id) {
        return jdbc.query("SELECT * FROM requirements WHERE local_id = ? FOR UPDATE",
                MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Requirement> lockFamilyInWorkingSet(Long trackerId, Long commonId) {
        return jdbc.query("""
                SELECT r.* FROM requirements r
                WHERE r.working_set_id = (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ? AND delete_flag = 0)
                  AND r.common_id = ?
                FOR UPDATE
                """, MAPPER, trackerId, commonId).stream().findFirst();
    }

    @Override
    public void purge(Requirement requirement) {
        long id = requirement.localId();
        jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", id);
        if (jdbc.update("DELETE FROM requirements WHERE local_id = ?", id) == 1) {
            commonInstances.remove(id);
        }
    }

    private void saveCommonInstance(long id) {
        AirInstCommonPo common = jdbc.queryForObject("""
                SELECT t.work_space, r.number, COALESCE(r.name, r.title) AS name, r.version, r.revision,
                       r.delete_flag, r.lock_version, r.common_id, r.working_set_id, r.tracker_id
                FROM requirements r JOIN air_inst_tracker_rev t ON t.id = r.tracker_id
                WHERE r.local_id = ?
                """, (rs, row) -> new AirInstCommonPo(id, 1401L, 140101L, 14010101L,
                rs.getObject("work_space", Long.class), rs.getString("number"), rs.getString("name"),
                null, rs.getString("version"), rs.getLong("revision"), rs.getBoolean("delete_flag") ? 1 : 0,
                rs.getLong("lock_version"), null, null, null, null, null, null, rs.getLong("common_id"),
                rs.getLong("working_set_id"), null, rs.getLong("tracker_id"), rs.getLong("revision")), id);
        commonInstances.save(common);
    }

    private Long currentLockVersion(Long localId) {
        Long value = jdbc.query(
                "SELECT lock_version FROM requirements WHERE local_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                localId);
        if (value == null) {
            throw DomainException.withMessage(FailureReason.NOT_FOUND, "Requirement no longer exists: " + localId);
        }
        return value;
    }
}
