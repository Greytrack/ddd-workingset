package sk.workingset.infrastructure.repositories;

import java.util.Optional;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import sk.workingset.domain.merge.MergeCommitReceipt;
import sk.workingset.domain.merge.MergeRepository;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.merge.Comparison.ChangeKind;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeCheckpoint.Direction;
import sk.workingset.domain.merge.MergeRound;

/** Merge 存储的 JDBC 实现：轮次状态按 JSONB 保存，比较结果不持久化。 */
public final class JdbcMergeRepository implements MergeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdentifierGenerator ids;

    public JdbcMergeRepository(JdbcTemplate jdbc, ObjectMapper json, IdentifierGenerator ids) {
        this.jdbc = jdbc;
        this.json = json;
        this.ids = ids;
    }

    @Override
    public Optional<MergeRound> insertOrFindRound(MergeRound round, TrackerId trackerA, TrackerId trackerB) {
        // Serialize duplicate requestId submissions before touching the unique indexes.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", round.requestId());
        return jdbc.query("""
                INSERT INTO merge_rounds (id, token, request_id, state, tracker_a_id, tracker_b_id)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                ON CONFLICT (request_id) DO UPDATE SET request_id = EXCLUDED.request_id
                RETURNING state
                """, (rs, row) -> decode(rs.getString(1), MergeRound.class),
                round.numericId(), round.token(), round.requestId(), encode(round),
                trackerA.numericValue(), trackerB.numericValue()).stream().findFirst();
    }

    @Override
    public Optional<MergeRound> findRound(String id, boolean lock) {
        String sql = "SELECT state FROM merge_rounds WHERE id = ?";
        return jdbc.query(lock ? sql + " FOR UPDATE" : sql,
                (rs, row) -> decode(rs.getString(1), MergeRound.class), Long.valueOf(id)).stream().findFirst();
    }

    @Override
    public Optional<MergeRound> findRoundByRequest(String requestId) {
        return jdbc.query("SELECT state FROM merge_rounds WHERE request_id = ?",
                (rs, row) -> decode(rs.getString(1), MergeRound.class), requestId).stream().findFirst();
    }

    @Override
    public void updateRound(MergeRound round) {
        jdbc.update("UPDATE merge_rounds SET state = CAST(? AS jsonb) WHERE id = ?", encode(round), round.numericId());
    }

    @Override
    public boolean tryLockExecution(String roundId) {
        return jdbc.query("""
                SELECT pg_try_advisory_xact_lock(id) FROM merge_rounds WHERE id = ?
                """, (rs, row) -> rs.getBoolean(1), Long.valueOf(roundId)).stream().findFirst().orElse(false);
    }

    @Override
    public Optional<MergeCommitReceipt> findCommitReceipt(String roundId, String entryId) {
        return jdbc.query("""
                SELECT business_ref, source_revision, target_revision FROM merge_commit_receipts
                WHERE round_id = ? AND entry_id = ?
                """, (rs, row) -> {
                    String businessRef = rs.getString(1);
                    return new MergeCommitReceipt(businessRef == null ? null : LocalId.of(businessRef),
                            revision(rs.getObject(2, Integer.class)), revision(rs.getObject(3, Integer.class)));
                }, Long.valueOf(roundId), Long.valueOf(entryId)).stream().findFirst();
    }

    @Override
    public void insertCommitReceipt(String roundId, String entryId, MergeCommitReceipt receipt) {
        jdbc.update("""
                INSERT INTO merge_commit_receipts (round_id, entry_id, business_ref, source_revision, target_revision)
                VALUES (?, ?, ?, ?, ?)
                """, Long.valueOf(roundId), Long.valueOf(entryId), receipt.businessRef() == null ? null : receipt.businessRef().numericValue(),
                revision(receipt.sourceRevision()), revision(receipt.targetRevision()));
    }

    @Override
    public long nextDecisionVersion() {
        return jdbc.queryForObject("SELECT nextval('merge_decision_version')", Long.class);
    }

    @Override
    public boolean recordCheckpoint(MergeCheckpoint checkpoint) {
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

    private static ChangeKind changeKind(String value) {
        return value == null ? null : ChangeKind.valueOf(value);
    }

    private static Direction direction(String value) {
        return value == null ? null : Direction.valueOf(value);
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

    private static Integer revision(Revision revision) { return revision == null ? null : revision.value(); }
    private static Revision revision(Integer revision) { return revision == null ? null : Revision.of(revision); }
    private static Long localId(LocalId localId) { return localId == null ? null : localId.numericValue(); }
    private static LocalId localId(String localId) { return localId == null ? null : LocalId.of(localId); }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize merge state", failure);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot reconstruct merge state", failure);
        }
    }
}
