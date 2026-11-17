package alm.requirement.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import sk.workingset.application.port.TrackerItemCatalog;

import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.RequirementCatalog.Identity;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

/**
 * Requirement 的 PostgreSQL/Spring JDBC 仓储。
 *
 * <p>写入使用 {@code lock_version} 条件更新：只有当前数据库版本等于待写入版本减一才更新；
 * 0 行受影响视为并发冲突，显式抛出 {@link LockVersionConflictException}，不静默覆盖。
 */
public class JdbcRequirementRepository implements RequirementRepository {

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
            LocalId.of(rs.getString("local_id")),
            CommonId.of(rs.getString("common_id")),
            TrackerId.of(rs.getString("tracker_id")),
            rs.getString("title"),
            rs.getString("description"),
            Revision.of(rs.getInt("revision")),
            rs.getString("version") == null ? Version.none() : Version.of(rs.getString("version")),
            LockVersion.of(rs.getLong("lock_version")), rs.getBoolean("delete_flag"));

    private final JdbcTemplate jdbc;

    public JdbcRequirementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Requirement requirement) {
        long lockVersion = requirement.lockVersion().value();
        if (lockVersion == 0) {
            jdbc.update(INSERT, requirement.localId().value(), requirement.commonId().value(),
                    requirement.trackerId().value(), requirement.title(), requirement.title(), requirement.description(),
                    requirement.revision().value(), requirement.version().value(), lockVersion, requirement.deleted());
            return;
        }
        long expected = lockVersion - 1;
        int affected = jdbc.update(UPDATE, requirement.title(), requirement.title(), requirement.description(),
                requirement.revision().value(), requirement.version().value(), requirement.deleted(), lockVersion,
                requirement.localId().value(), expected);
        if (affected == 0) {
            throw new LockVersionConflictException(LockVersion.of(expected), currentLockVersion(requirement.localId()));
        }
    }

    @Override
    public Optional<Requirement> findById(LocalId localId) {
        return jdbc.query(
                "SELECT local_id, common_id, tracker_id, title, description, revision, version, lock_version, delete_flag "
                        + "FROM requirements WHERE local_id = ?",
                MAPPER,
                localId.value()).stream().findFirst();
    }

    /** UI read model: display name of the content source, falling back to title for legacy rows. */
    public Optional<String> findName(LocalId localId) {
        return jdbc.query("SELECT COALESCE(name, title) FROM requirements WHERE local_id = ?",
                rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.<String>empty(),
                localId.value());
    }

    public List<Identity> findIdentitiesByTracker(TrackerId trackerId) {
        return jdbc.query("SELECT local_id, common_id FROM requirements "
                        + "WHERE tracker_id = ? AND delete_flag = FALSE ORDER BY local_id",
                (rs, row) -> new Identity(LocalId.of(rs.getString(1)), CommonId.of(rs.getString(2))), trackerId.value());
    }

    public List<TrackerItemCatalog.Identity> pageActiveIdentities(TrackerId trackerId, String afterLocalId, int limit) {
        return jdbc.query("""
                SELECT local_id, common_id, revision FROM requirements
                WHERE tracker_id = ? AND NOT delete_flag AND local_id COLLATE "C" > ?
                ORDER BY local_id COLLATE "C" LIMIT ?
                """, (rs, row) -> new TrackerItemCatalog.Identity(LocalId.of(rs.getString(1)),
                CommonId.of(rs.getString(2)), Revision.of(rs.getInt(3))), trackerId.value(), afterLocalId, limit);
    }

    public boolean hasActiveFamily(TrackerId trackerId, CommonId commonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM requirements WHERE tracker_id = ? AND common_id = ? AND NOT delete_flag)
                """, Boolean.class, trackerId.value(), commonId.value()));
    }

    public Optional<Requirement> lockById(LocalId id) {
        return jdbc.query("SELECT * FROM requirements WHERE local_id = ? FOR UPDATE",
                MAPPER, id.value()).stream().findFirst();
    }

    public Optional<Requirement> lockFamilyInWorkingSet(TrackerId trackerId, CommonId commonId) {
        return jdbc.query("""
                SELECT r.* FROM requirements r
                WHERE r.working_set_id = (SELECT working_set_id FROM trackers WHERE tracker_id = ?)
                  AND r.common_id = ?
                FOR UPDATE
                """, MAPPER, trackerId.value(), commonId.value()).stream().findFirst();
    }

    public void purge(Requirement requirement) {
        jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", requirement.localId().value());
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", requirement.localId().value());
    }

    public boolean existsInTracker(TrackerId trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM requirements WHERE tracker_id = ?)", Boolean.class, trackerId.value()));
    }

    private LockVersion currentLockVersion(LocalId localId) {
        Long value = jdbc.query(
                "SELECT lock_version FROM requirements WHERE local_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                localId.value());
        if (value == null) {
            throw new ApplicationFailure(FailureReason.NOT_FOUND, "Requirement no longer exists: " + localId.value());
        }
        return LockVersion.of(value);
    }
}
