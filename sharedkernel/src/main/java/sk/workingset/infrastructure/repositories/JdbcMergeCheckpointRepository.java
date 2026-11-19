package sk.workingset.infrastructure.repositories;

import java.util.Optional;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison.ChangeKind;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeCheckpoint.Direction;
import sk.workingset.domain.merge.MergeCheckpointRepository;

@Repository
@RequiredArgsConstructor
public class JdbcMergeCheckpointRepository implements MergeCheckpointRepository {
    private final JdbcTemplate jdbc;
    private final IdentifierGenerator ids;

    private static ChangeKind changeKind(String value) {
        return value == null ? null : ChangeKind.valueOf(value);
    }

    private static Direction direction(String value) {
        return value == null ? null : Direction.valueOf(value);
    }

    private static Integer revision(Revision revision) {
        return revision == null ? null : revision.value();
    }

    private static Revision revision(Integer revision) {
        return revision == null ? null : Revision.of(revision);
    }

    private static Long localId(LocalId localId) {
        return localId == null ? null : localId.numericValue();
    }

    private static LocalId localId(String localId) {
        return localId == null ? null : LocalId.of(localId);
    }

    @Override
    public long nextDecisionVersion() {
        return jdbc.queryForObject("SELECT nextval('merge_decision_version')", Long.class);
    }

    @Override
    public boolean save(MergeCheckpoint checkpoint) {
        MergeCheckpoint.Key key = checkpoint.key();
        return jdbc.update("""
                        INSERT INTO merge_checkpoints (checkpoint_id, round_id, tracker_a_id, tracker_b_id, common_id,
                            local_id_a, local_id_b, revision_a, revision_b, change_kind, merge_direction,
                            decision_version, revoked, operator)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tracker_a_id, tracker_b_id, common_id) DO UPDATE SET
                            round_id = EXCLUDED.round_id,
                            local_id_a = EXCLUDED.local_id_a, local_id_b = EXCLUDED.local_id_b,
                            revision_a = EXCLUDED.revision_a, revision_b = EXCLUDED.revision_b,
                            change_kind = EXCLUDED.change_kind, merge_direction = EXCLUDED.merge_direction,
                            decision_version = EXCLUDED.decision_version, revoked = EXCLUDED.revoked,
                            operator = EXCLUDED.operator, confirmed_at = now(), updated_at = now()
                        WHERE merge_checkpoints.decision_version < EXCLUDED.decision_version
                           OR (merge_checkpoints.decision_version = EXCLUDED.decision_version
                               AND EXCLUDED.revoked AND NOT merge_checkpoints.revoked)
                        """, ids.nextId(null).longValue(), Long.valueOf(checkpoint.roundId()), key.a().numericValue(), key.b().numericValue(), key.family().numericValue(),
                localId(checkpoint.localIdA()), localId(checkpoint.localIdB()),
                revision(checkpoint.revisionA()), revision(checkpoint.revisionB()),
                checkpoint.changeKind() == null ? null : checkpoint.changeKind().name(),
                checkpoint.direction().name(), checkpoint.decisionVersion(), checkpoint.revoked(),
                checkpoint.operator()) == 1;
    }

    @Override
    public Optional<MergeCheckpoint> findCheckpoint(MergeCheckpoint.Key key) {
        return jdbc.query("""
                        SELECT round_id, local_id_a, local_id_b, revision_a, revision_b, change_kind,
                               merge_direction, decision_version, revoked, operator
                        FROM merge_checkpoints
                        WHERE tracker_a_id = ? AND tracker_b_id = ? AND common_id = ?
                        """, (rs, row) -> new MergeCheckpoint(key, rs.getLong(8),
                        revision(rs.getObject(4, Integer.class)), revision(rs.getObject(5, Integer.class)),
                        localId(rs.getString(2)), localId(rs.getString(3)), rs.getString(1),
                        changeKind(rs.getString(6)), direction(rs.getString(7)), rs.getBoolean(9), rs.getString(10)),
                key.a().numericValue(), key.b().numericValue(), key.family().numericValue()).stream().findFirst();
    }

    @Override
    public boolean revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion, long newVersion) {
        return jdbc.update("""
                UPDATE merge_checkpoints SET revoked = TRUE, decision_version = ?, updated_at = now()
                WHERE tracker_a_id = ? AND tracker_b_id = ? AND common_id = ?
                    AND decision_version = ? AND NOT revoked
                """, newVersion, key.a().numericValue(), key.b().numericValue(), key.family().numericValue(), expectedVersion) == 1;
    }

    @Override
    public int deleteSupersededCheckpoints() {
        return jdbc.update("""
                DELETE FROM merge_checkpoints c
                WHERE c.revoked = FALSE
                  AND EXISTS (SELECT 1 FROM light_tracker_items a
                              WHERE a.tracker_id = c.tracker_a_id AND a.common_id = c.common_id
                                AND a.revision > c.revision_a)
                  AND EXISTS (SELECT 1 FROM light_tracker_items b
                              WHERE b.tracker_id = c.tracker_b_id AND b.common_id = c.common_id
                                AND b.revision > c.revision_b)
                """);
    }

}
