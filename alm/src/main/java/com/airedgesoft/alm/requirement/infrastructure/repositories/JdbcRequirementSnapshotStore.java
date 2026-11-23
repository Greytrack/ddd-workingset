package com.airedgesoft.alm.requirement.infrastructure.repositories;


import com.airedgesoft.alm.requirement.application.command.RequirementSnapshot;
import com.airedgesoft.alm.requirement.domain.RequirementSnapshotStore;
import com.airedgesoft.sharedkernel.workingset.domain.event.SnapshotRefEvent;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Requirement 修改前快照的 PostgreSQL/Spring JDBC 存储。
 */
@Component
@RequiredArgsConstructor
public class JdbcRequirementSnapshotStore implements RequirementSnapshotStore {

    private static final String INSERT = """
            INSERT INTO requirement_snapshots
                (snapshot_ref, local_id, common_id, tracker_id, title, description,
                 revision, version, lock_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final RowMapper<RequirementSnapshot> MAPPER = (rs, rowNum) -> new RequirementSnapshot(
            rs.getLong("local_id"),
            rs.getLong("common_id"),
            rs.getLong("tracker_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getInt("revision"),
            rs.getString("version"),
            rs.getLong("lock_version"));

    private final JdbcTemplate jdbc;
    private final IdentifierGenerator identifierGenerator;


    @Override
    public SnapshotRefEvent save(RequirementSnapshot snapshot) {
        SnapshotRefEvent ref = SnapshotRefEvent.of(String.valueOf(identifierGenerator.nextId(null).longValue()));
        jdbc.update(INSERT,
                ref.numericId(),
                snapshot.localId(),
                snapshot.commonId(),
                snapshot.trackerId(),
                snapshot.title(),
                snapshot.description(),
                snapshot.revision(),
                snapshot.version(),
                snapshot.lockVersion());
        return ref;
    }

    @Override
    public Optional<RequirementSnapshot> find(SnapshotRefEvent ref) {
        return jdbc.query(
                "SELECT snapshot_ref, local_id, common_id, tracker_id, title, description, "
                        + "revision, version, lock_version FROM requirement_snapshots WHERE snapshot_ref = ?",
                MAPPER,
                ref.numericId()).stream().findFirst();
    }
}
