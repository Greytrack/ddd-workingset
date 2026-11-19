package sk.workingset.infrastructure.repositories;

import sk.SharedKernelResultCode;
import sk.common.error.DomainException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sk.workingset.domain.merge.Comparison;
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
    public void save(MergeRound round) {
        // Serialize duplicate requestId submissions before touching the unique indexes.
        // The advisory lock is transaction scoped: the first committer owns the requestId,
        // later submissions leave the winning round untouched and re-read it by requestId.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", round.requestId());
        Long owner = jdbc.query("SELECT id FROM merge_rounds WHERE request_id = ?",
                (rs, row) -> rs.getLong(1), round.requestId()).stream().findFirst().orElse(null);
        if (owner != null && !owner.equals(round.numericId())) {
            return;
        }
        Long[] trackers = trackerPair(round);
        // One statement serves both insert (new round) and update (state transition on an existing round).
        // A tracker pair conflict on the active partial unique index still surfaces as
        // DataIntegrityViolationException for the application layer to translate.
        jdbc.update("""
                        INSERT INTO merge_rounds (id, token, request_id, state, tracker_a_id, tracker_b_id)
                        VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                        ON CONFLICT (id) DO UPDATE
                        SET state = EXCLUDED.state,
                            tracker_a_id = EXCLUDED.tracker_a_id,
                            tracker_b_id = EXCLUDED.tracker_b_id
                        """, round.numericId(), round.token(), round.requestId(), encode(round),
                trackers[0], trackers[1]);
    }

    @Override
    public Optional<MergeRound> findById(String id) {
        // 命令侧（dispatch/resolve）在事务内读出轮次后修改，需要行锁串行化同一轮次的并发收敛；
        // 普通读取（GET）不在事务内，不额外加锁，与旧实现的 lock=false 行为保持一致。
        String sql = "SELECT state FROM merge_rounds WHERE id = ?"
                + (TransactionSynchronizationManager.isActualTransactionActive() ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> decode(rs.getString(1), MergeRound.class),
                Long.valueOf(id)).stream().findFirst();
    }

    @Override
    public Optional<MergeRound> findByRequestId(String requestId) {
        return jdbc.query("SELECT state FROM merge_rounds WHERE request_id = ?",
                (rs, row) -> decode(rs.getString(1), MergeRound.class), requestId).stream().findFirst();
    }

    /**
     * 活动轮次唯一索引按数值升序记录 Tracker 对；聚合内证据已保证整轮同属一对。
     */
    private static Long[] trackerPair(MergeRound round) {
        MergeRound.Entry first = round.entries().isEmpty() ? null : round.entries().getFirst();
        if (first == null || first.evidence() == null) {
            return new Long[]{null, null};
        }
        Comparison.TrackerPair trackers = first.evidence().trackers();
        long a = trackers.source().numericValue();
        long b = trackers.target().numericValue();
        return a <= b ? new Long[]{a, b} : new Long[]{b, a};
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_SERIALIZATION_FAILED, failure);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_SERIALIZATION_FAILED, failure);
        }
    }
}
