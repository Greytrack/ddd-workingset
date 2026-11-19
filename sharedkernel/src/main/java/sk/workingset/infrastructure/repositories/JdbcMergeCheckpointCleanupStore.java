package sk.workingset.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import sk.workingset.domain.merge.MergeCheckpointCleanupStore;

/** Checkpoint 清理的 JDBC 维护存储。 */
@Component
@RequiredArgsConstructor
public class JdbcMergeCheckpointCleanupStore implements MergeCheckpointCleanupStore {
    private final JdbcTemplate jdbc;

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
