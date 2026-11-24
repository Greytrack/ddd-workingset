package com.airedgesoft.lib.restful.outbox;

/**
 * Outbox 本地消息表记录（Transactional Outbox 模式；跨聚合写与事件最终一致性基建）。
 *
 * <p>
 * 载荷为 JSON 字符串（领域事件的可序列化快照，由 BC 定义事件契约）；派发器（{@link OutboxDispatcher}）轮询未发布记录并回调 BC 的 发布器（如 Modulith 事件网关 / MQ
 * producer）。同事务写入业务表 + outbox 表，派发异步——本地事务原子性与跨聚合/跨进程通知解耦。
 *
 * @param id
 *            记录主键（UUID，派发幂等的去重锚点）
 * @param eventType
 *            事件类型（如 {@code demo.task.created}）
 * @param aggregateType
 *            聚合类型（如 {@code demoTasks}）
 * @param aggregateId
 *            聚合契约 ID
 * @param payload
 *            事件载荷 JSON
 * @param createTimeMs
 *            入箱时刻（Unix Epoch 毫秒）
 * @param publishedTimeMs
 *            发布完成时刻（未发布为 null）
 */
public record OutboxRecord(String id, String eventType, String aggregateType, String aggregateId, String payload,
        long createTimeMs, Long publishedTimeMs) {

    /** 新建未发布记录。 */
    public static OutboxRecord pending(String id, String eventType, String aggregateType, String aggregateId,
            String payload) {
        return new OutboxRecord(id, eventType, aggregateType, aggregateId, payload, System.currentTimeMillis(), null);
    }

    /** 是否已发布。 */
    public boolean published() {
        return publishedTimeMs != null;
    }

    /** 已发布副本。 */
    public OutboxRecord asPublished() {
        return new OutboxRecord(id, eventType, aggregateType, aggregateId, payload, createTimeMs,
                System.currentTimeMillis());
    }
}
