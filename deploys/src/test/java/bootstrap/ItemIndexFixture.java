package bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 测试夹具：直接写 {@code air_ws_items_index} 与 {@code air_ws_items_revision_index}。
 *
 * <p>索引物理表即 V3 存储；测试不再经过任何旧表视图或触发器适配。
 */
public final class ItemIndexFixture {
    private ItemIndexFixture() {
    }

    /**
     * 以给定来源证据写入一条 Item 索引，并登记其版次成员。
     *
     * <p>{@code materialized} 为真时要求 {@code sourceLocalId} 等于 {@code localId} 且
     * {@code sourceRevision} 等于 {@code revision}，与索引表的一致性约束保持一致。
     */
    public static void insert(JdbcTemplate jdbc, String localId, String commonId, String trackerId,
                              String workingSetId, int revision, String sourceLocalId, int sourceRevision,
                              boolean materialized) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO air_ws_items_index
                    (id, local_id, common_id, branch_id, workingset_id, revision, source_local_id, source_revision,
                     has_copied, flags, work_space_id, master_id, parent_branch_id, sub_class_id, created_by, created_time)
                SELECT ?, ?, ?, t.id, ?, ?, ?, ?, ?, 0, t.work_space, t.common_id, t.source_branch_id, t.tracker_class,
                       'test', ?
                FROM air_inst_tracker_rev t WHERE t.id = ?
                """, Long.valueOf(localId), Long.valueOf(localId), Long.valueOf(commonId), Long.valueOf(workingSetId),
                revision, Long.valueOf(sourceLocalId), sourceRevision, materialized ? 1 : 0, now,
                Long.valueOf(trackerId));
        jdbc.update("""
                INSERT INTO air_ws_items_revision_index
                    (id, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, revision, sub_class_id)
                SELECT ?, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, ?, sub_class_id
                FROM air_ws_items_index WHERE local_id = ?
                ON CONFLICT (local_id, revision) DO NOTHING
                """, Long.valueOf(localId), revision, Long.valueOf(localId));
    }

    /** 只改当前索引的修订号，并同步其版次成员，等价于旧夹具视图的 UPDATE 语义。 */
    public static void updateRevision(JdbcTemplate jdbc, String localId, int revision) {
        jdbc.update("UPDATE air_ws_items_index SET revision = ? WHERE local_id = ?", revision, Long.valueOf(localId));
        jdbc.update("UPDATE air_ws_items_revision_index SET revision = ? WHERE local_id = ?", revision, Long.valueOf(localId));
    }

    /** 只改当前索引的家族身份，并同步其版次成员，等价于旧夹具视图的 UPDATE 语义。 */
    public static void updateFamily(JdbcTemplate jdbc, String localId, String commonId) {
        jdbc.update("UPDATE air_ws_items_index SET common_id = ? WHERE local_id = ?", Long.valueOf(commonId),
                Long.valueOf(localId));
        jdbc.update("UPDATE air_ws_items_revision_index SET common_id = ? WHERE local_id = ?", Long.valueOf(commonId),
                Long.valueOf(localId));
    }
}
