package sk.workingset.infrastructure.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.merge.MergeRepository;
import sk.workingset.domain.merge.MergeRound;

import java.util.Optional;

/**
 * Merge 存储的 JDBC 实现：轮次状态按 JSONB 保存，比较结果不持久化。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcMergeRepositoryImpl implements MergeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Override
    public Optional<MergeRound> admit(MergeRound round, TrackerId trackerA, TrackerId trackerB) {
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
    public void save(MergeRound round) {
        jdbc.update("UPDATE merge_rounds SET state = CAST(? AS jsonb) WHERE id = ?", encode(round), round.numericId());
    }

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
