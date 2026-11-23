package com.airedgesoft.sharedkernel.workingset.infrastructure.repositories;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRepository;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound;
import com.airedgesoft.sharedkernel.workingset.error.MergeResCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Merge 存储的 JDBC 实现：轮次状态按 JSONB 保存，比较结果不持久化。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcMergeRepositoryImpl implements MergeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /**
     * 活动轮次唯一索引按数值升序记录 Tracker 对；聚合内证据已保证整轮同属一对。
     */
    private static Long[] trackerPair(MergeRound round) {
        MergeRound.Entry first = round.entries().isEmpty() ? null : round.entries().getFirst();
        if (first == null || first.evidence() == null) {
            return new Long[]{null, null};
        }
        Comparison.TrackerPair trackers = first.evidence().trackers();
        long a = trackers.source();
        long b = trackers.target();
        return a <= b ? new Long[]{a, b} : new Long[]{b, a};
    }

    @Override
    public boolean claim(MergeRound round) {
        Long[] trackers = trackerPair(round);
        int inserted = jdbc.update("""
                        INSERT INTO merge_rounds (id, token, request_id, state, tracker_a_id, tracker_b_id)
                        VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
                        ON CONFLICT (request_id) DO NOTHING
                        """, round.numericId(), round.token(), round.requestId(), encode(round),
                trackers[0], trackers[1]);
        return inserted == 1;
    }

    @Override
    public void save(MergeRound round) {
        Long[] trackers = trackerPair(round);
        int affected = jdbc.update("""
                UPDATE merge_rounds
                SET state = CAST(? AS jsonb),
                    tracker_a_id = ?,
                    tracker_b_id = ?
                WHERE id = ?
                """, encode(round), trackers[0], trackers[1], round.numericId());
        if (affected != 1) {
            throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID,
                    "Could not update merge round " + round.id());
        }
        saveConfirmations(round);
    }

    private void saveConfirmations(MergeRound round) {
        List<MergeRound.Entry> confirmed = round.entries().stream()
                .filter(entry -> entry.confirmation() != null).toList();
        if (confirmed.isEmpty()) {
            return;
        }
        Set<Long> saved = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM air_ws_items_merge_index WHERE batch_id = ?", Long.class, round.numericId()));
        for (MergeRound.Entry entry : confirmed) {
            if (!saved.contains(entry.numericId())) {
                insertConfirmation(round, entry);
            }
        }
    }

    private void insertConfirmation(MergeRound round, MergeRound.Entry entry) {
        Comparison.Difference evidence = entry.evidence();
        MergeRound.Confirmation confirmation = entry.confirmation();
        long source = evidence.trackers().source();
        long target = evidence.trackers().target();
        boolean sourceIsA = source <= target;
        Long sourceLocal = evidence.source().localId();
        Long targetLocal = entry.result().businessRef() != null
                ? Long.valueOf(entry.result().businessRef()) : evidence.target().localId();
        String diffType = switch (evidence.kind()) {
            case ADDED -> "ADD";
            case MODIFIED -> "UPDATED";
            case DELETED -> "DELETED";
        };
        int inserted = jdbc.update("""
                        INSERT INTO air_ws_items_merge_index
                            (id, batch_id, branch_a_id, branch_b_id, common_id, local_id_a, local_id_b,
                             revision_a, revision_b, diff_type, merge_direction, merge_action, merge_status,
                             operator, created_by, created_time, updated_by, updated_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MERGED', ?, ?,
                            (extract(epoch FROM now()) * 1000)::bigint, ?, (extract(epoch FROM now()) * 1000)::bigint)
                        ON CONFLICT (id) DO NOTHING
                        """, entry.numericId(), round.numericId(), Math.min(source, target), Math.max(source, target),
                evidence.family(), sourceIsA ? sourceLocal : targetLocal, sourceIsA ? targetLocal : sourceLocal,
                sourceIsA ? confirmation.sourceRevision() : confirmation.targetRevision(),
                sourceIsA ? confirmation.targetRevision() : confirmation.sourceRevision(), diffType,
                sourceIsA ? "A_TO_B" : "B_TO_A",
                "IGNORED".equals(entry.result().operation()) ? "IGNORED" : "ACCEPTED",
                round.caller(), round.caller(), round.caller());
        if (inserted != 1) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "checkpoint ID belongs to another confirmation");
        }
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
