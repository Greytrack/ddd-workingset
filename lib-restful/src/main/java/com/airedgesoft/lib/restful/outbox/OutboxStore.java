package com.airedgesoft.lib.restful.outbox;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 存储端口（缺省 {@link JdbcOutboxStore}——与业务表同库同事务写入；移植到 MQ 中转表或 Redis Stream 时实现本接口）。
 */
public interface OutboxStore {

    /** 入箱（与业务写同事务调用）。 */
    void append(OutboxRecord record);

    /** 从队首读取未发布记录。 */
    default List<OutboxRecord> fetchUnpublished(int limit) {
        return fetchUnpublished(limit, null);
    }

    /** 按入箱顺序读取有限批次，跳过已经扫描的位置。 */
    default List<OutboxRecord> fetchUnpublished(int limit, OutboxRecord after) {
        return fetchUnpublished(limit, after, null);
    }

    /** 按入箱时间和 ID 升序读取，起点排除，终点包含，空边界表示不限。 */
    List<OutboxRecord> fetchUnpublished(int limit, OutboxRecord after, OutboxRecord through);

    /** 取得本轮扫描开始时的队尾位置。 */
    Optional<OutboxRecord> findLastUnpublished();

    /** 标记已发布（派发器回调成功后）。 */
    void markPublished(String id);
}
