package alm.requirement.infrastructure;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
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

    private static final String UPSERT = """
            INSERT INTO requirements
                (local_id, common_id, tracker_id, title, description, revision, version, lock_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (local_id) DO UPDATE SET
                common_id = EXCLUDED.common_id,
                tracker_id = EXCLUDED.tracker_id,
                title = EXCLUDED.title,
                description = EXCLUDED.description,
                revision = EXCLUDED.revision,
                version = EXCLUDED.version,
                lock_version = EXCLUDED.lock_version
            WHERE requirements.lock_version = EXCLUDED.lock_version - 1
            """;

    private static final RowMapper<Requirement> MAPPER = (rs, rowNum) -> Requirement.restore(
            LocalId.of(rs.getString("local_id")),
            CommonId.of(rs.getString("common_id")),
            TrackerId.of(rs.getString("tracker_id")),
            rs.getString("title"),
            rs.getString("description"),
            Revision.of(rs.getInt("revision")),
            rs.getString("version") == null ? Version.none() : Version.of(rs.getString("version")),
            LockVersion.of(rs.getLong("lock_version")));

    private final JdbcTemplate jdbc;

    public JdbcRequirementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Requirement requirement) {
        int affected = jdbc.update(UPSERT,
                requirement.localId().value(),
                requirement.commonId().value(),
                requirement.trackerId().value(),
                requirement.title(),
                requirement.description(),
                requirement.revision().value(),
                requirement.version().value(),
                requirement.lockVersion().value());
        if (affected == 0) {
            LockVersion actual = currentLockVersion(requirement.localId());
            long expectedValue = Math.max(0L, requirement.lockVersion().value() - 1L);
            throw new LockVersionConflictException(LockVersion.of(expectedValue), actual);
        }
    }

    @Override
    public Optional<Requirement> findById(LocalId localId) {
        return jdbc.query(
                "SELECT local_id, common_id, tracker_id, title, description, revision, version, lock_version "
                        + "FROM requirements WHERE local_id = ?",
                MAPPER,
                localId.value()).stream().findFirst();
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
            throw new IllegalStateException("requirement not found after conflict: " + localId.value());
        }
        return LockVersion.of(value);
    }
}
