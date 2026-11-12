package alm.requirement.infrastructure;

import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

/**
 * Requirement 修改前快照的 PostgreSQL/Spring JDBC 存储。
 */
public class JdbcSnapshotStore implements SnapshotStore {

    private static final String INSERT = """
            INSERT INTO requirement_snapshots
                (snapshot_ref, local_id, common_id, tracker_id, title, description,
                 revision, version, lock_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final RowMapper<RequirementSnapshot> MAPPER = (rs, rowNum) -> new RequirementSnapshot(
            LocalId.of(rs.getString("local_id")),
            CommonId.of(rs.getString("common_id")),
            TrackerId.of(rs.getString("tracker_id")),
            rs.getString("title"),
            rs.getString("description"),
            Revision.of(rs.getInt("revision")),
            rs.getString("version") == null ? Version.none() : Version.of(rs.getString("version")),
            LockVersion.of(rs.getLong("lock_version")));

    private final JdbcTemplate jdbc;
    private final IdentifierGenerator identifierGenerator;

    public JdbcSnapshotStore(JdbcTemplate jdbc, IdentifierGenerator identifierGenerator) {
        this.jdbc = jdbc;
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
    }

    @Override
    public SnapshotRef save(RequirementSnapshot snapshot) {
        SnapshotRef ref = SnapshotRef.of(String.valueOf(identifierGenerator.nextId()));
        jdbc.update(INSERT,
                ref.id(),
                snapshot.localId().value(),
                snapshot.commonId().value(),
                snapshot.trackerId().value(),
                snapshot.title(),
                snapshot.description(),
                snapshot.revision().value(),
                snapshot.version().value(),
                snapshot.lockVersion().value());
        return ref;
    }

    @Override
    public Optional<RequirementSnapshot> find(SnapshotRef ref) {
        return jdbc.query(
                "SELECT snapshot_ref, local_id, common_id, tracker_id, title, description, "
                        + "revision, version, lock_version FROM requirement_snapshots WHERE snapshot_ref = ?",
                MAPPER,
                ref.id()).stream().findFirst();
    }
}
