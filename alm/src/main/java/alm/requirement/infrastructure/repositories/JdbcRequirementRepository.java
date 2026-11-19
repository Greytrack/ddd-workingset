package alm.requirement.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.application.port.RequirementMutationStore;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.Version;

/**
 * Requirement 的 PostgreSQL/Spring JDBC 仓储。
 *
 * <p>写入使用 {@code lock_version} 条件更新：只有当前数据库版本等于待写入版本减一才更新；
 * 0 行受影响视为并发冲突，显式抛出 {@link LockVersionConflictException}，不静默覆盖。
 */
@Repository
@RequiredArgsConstructor
public class JdbcRequirementRepository implements RequirementMutationStore {

    private static final String INSERT = """
            INSERT INTO requirements
                (local_id, common_id, tracker_id, name, title, description, revision, version, lock_version, delete_flag)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE requirements
            SET name = ?, title = ?, description = ?, revision = ?, version = ?, delete_flag = ?, lock_version = ?
            WHERE local_id = ? AND lock_version = ?
            """;

    private static final RowMapper<Requirement> MAPPER = (rs, rowNum) -> Requirement.restore(
            LocalId.of(rs.getLong("local_id")),
            CommonId.of(rs.getLong("common_id")),
            TrackerId.of(rs.getLong("tracker_id")),
            rs.getString("title"),
            rs.getString("description"),
            Revision.of(rs.getInt("revision")),
            rs.getString("version") == null ? Version.none() : Version.of(rs.getString("version")),
            LockVersion.of(rs.getLong("lock_version")), rs.getBoolean("delete_flag"));

    private final JdbcTemplate jdbc;
    private final JdbcCommonInstanceStore commonInstances;


    @Override
    @Transactional
    public void save(Requirement requirement) {
        long lockVersion = requirement.lockVersion().value();
        if (lockVersion == 0) {
            jdbc.update(INSERT, requirement.localId().numericValue(), requirement.commonId().numericValue(),
                    requirement.trackerId().numericValue(), requirement.title(), requirement.title(), requirement.description(),
                    requirement.revision().value(), requirement.version().value(), lockVersion, requirement.deleted());
            saveCommonInstance(requirement.localId().numericValue());
            return;
        }
        long expected = lockVersion - 1;
        int affected = jdbc.update(UPDATE, requirement.title(), requirement.title(), requirement.description(),
                requirement.revision().value(), requirement.version().value(), requirement.deleted(), lockVersion,
                requirement.localId().numericValue(), expected);
        if (affected == 0) {
            throw new LockVersionConflictException(LockVersion.of(expected), currentLockVersion(requirement.localId()));
        }
        saveCommonInstance(requirement.localId().numericValue());
    }

    @Override
    public Optional<Requirement> findById(LocalId localId) {
        return jdbc.query(
                "SELECT local_id, common_id, tracker_id, title, description, revision, version, lock_version, delete_flag "
                        + "FROM requirements WHERE local_id = ?",
                MAPPER,
                localId.numericValue()).stream().findFirst();
    }

    @Override
    public Optional<Requirement> lockById(LocalId id) {
        return jdbc.query("SELECT * FROM requirements WHERE local_id = ? FOR UPDATE",
                MAPPER, id.numericValue()).stream().findFirst();
    }

    @Override
    public Optional<Requirement> lockFamilyInWorkingSet(TrackerId trackerId, CommonId commonId) {
        return jdbc.query("""
                SELECT r.* FROM requirements r
                WHERE r.working_set_id = (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ? AND delete_flag = 0)
                  AND r.common_id = ?
                FOR UPDATE
                """, MAPPER, trackerId.numericValue(), commonId.numericValue()).stream().findFirst();
    }

    @Override
    @Transactional
    public void purge(Requirement requirement) {
        long id = requirement.localId().numericValue();
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

    public boolean existsInTracker(TrackerId trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM requirements WHERE tracker_id = ?)", Boolean.class, trackerId.numericValue()));
    }

    private LockVersion currentLockVersion(LocalId localId) {
        Long value = jdbc.query(
                "SELECT lock_version FROM requirements WHERE local_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                localId.numericValue());
        if (value == null) {
            throw DomainException.withMessage(FailureReason.NOT_FOUND, "Requirement no longer exists: " + localId.numericValue());
        }
        return LockVersion.of(value);
    }
}
