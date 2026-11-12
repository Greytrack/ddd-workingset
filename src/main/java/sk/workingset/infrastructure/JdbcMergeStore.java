package sk.workingset.infrastructure;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import sk.workingset.application.port.MergeStore;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;

public final class JdbcMergeStore implements MergeStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcMergeStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insertComparison(Comparison comparison) {
        jdbc.update("INSERT INTO merge_comparisons (token, evidence) VALUES (?, CAST(? AS jsonb))",
                comparison.token(), encode(comparison));
    }

    @Override
    public Optional<Comparison> findComparison(String token) {
        return jdbc.query("SELECT evidence FROM merge_comparisons WHERE token = ?",
                (rs, row) -> decode(rs.getString(1), Comparison.class), token).stream().findFirst();
    }

    @Override
    public boolean insertRound(MergeRound round) {
        return jdbc.update("""
                INSERT INTO merge_rounds (id, token, state) VALUES (?, ?, CAST(? AS jsonb))
                ON CONFLICT (token) DO NOTHING
                """, round.id(), round.token(), encode(round)) == 1;
    }

    @Override
    public Optional<MergeRound> findRound(String id, boolean lock) {
        String sql = "SELECT state FROM merge_rounds WHERE id = ?";
        return jdbc.query(lock ? sql + " FOR UPDATE" : sql,
                (rs, row) -> decode(rs.getString(1), MergeRound.class), id).stream().findFirst();
    }

    @Override
    public void updateRound(MergeRound round) {
        jdbc.update("UPDATE merge_rounds SET state = CAST(? AS jsonb) WHERE id = ?", encode(round), round.id());
    }

    @Override
    public long nextDecisionVersion() {
        return jdbc.queryForObject("SELECT nextval('merge_decision_version')", Long.class);
    }

    @Override
    public boolean recordCheckpoint(MergeCheckpoint checkpoint) {
        var key = checkpoint.key();
        return jdbc.update("""
                INSERT INTO merge_checkpoints (source_id, target_id, common_id, decision_version,
                    source_revision, target_revision, token, revoked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, target_id, common_id) DO UPDATE SET
                    decision_version = EXCLUDED.decision_version, source_revision = EXCLUDED.source_revision,
                    target_revision = EXCLUDED.target_revision, token = EXCLUDED.token, revoked = EXCLUDED.revoked
                WHERE merge_checkpoints.decision_version < EXCLUDED.decision_version
                   OR (merge_checkpoints.decision_version = EXCLUDED.decision_version
                       AND EXCLUDED.revoked AND NOT merge_checkpoints.revoked)
                """, key.source().value(), key.target().value(), key.family().value(), checkpoint.decisionVersion(),
                revision(checkpoint.sourceRevision()), revision(checkpoint.targetRevision()),
                checkpoint.token(), checkpoint.revoked()) == 1;
    }

    @Override
    public Optional<MergeCheckpoint> findCheckpoint(MergeCheckpoint.Key key) {
        return jdbc.query("""
                SELECT decision_version, source_revision, target_revision, token, revoked
                FROM merge_checkpoints WHERE source_id = ? AND target_id = ? AND common_id = ?
                """, (rs, row) -> new MergeCheckpoint(key, rs.getLong(1),
                revision(rs.getObject(2, Integer.class)), revision(rs.getObject(3, Integer.class)),
                rs.getString(4), rs.getBoolean(5)), key.source().value(), key.target().value(), key.family().value())
                .stream().findFirst();
    }

    @Override
    public boolean revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion, long newVersion) {
        return jdbc.update("""
                UPDATE merge_checkpoints SET revoked = TRUE, decision_version = ?
                WHERE source_id = ? AND target_id = ? AND common_id = ?
                    AND decision_version = ? AND NOT revoked
                """, newVersion, key.source().value(), key.target().value(), key.family().value(),
                expectedVersion) == 1;
    }

    private static Integer revision(Revision revision) { return revision == null ? null : revision.value(); }
    private static Revision revision(Integer revision) { return revision == null ? null : Revision.of(revision); }

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
