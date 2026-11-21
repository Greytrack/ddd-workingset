package com.airedgesoft.lib.jdbc;

/**
 * 聚合持久化生命周期监听器：解耦底层增量写入技术与上层协议/观测（如 ETag、审计）。
 */
public interface AggregateLifecycleListener {

    /**
     * 聚合从快照重建后回调。
     *
     * @param identity  聚合身份
     * @param aggregate 重建出的聚合对象
     * @param version   原始存储版本（null 表示未配置或旧库 NULL 版本）
     */
    void onLoaded(Object identity, Object aggregate, Long version);

    /**
     * 聚合保存成功后回调（仅在实际写入（{@code Receipt.Applied}）时触发；无变化（NoChanges）不回调）。
     *
     * @param identity   聚合身份
     * @param aggregate  已持久化的聚合对象
     * @param newVersion 保存后推进的最新锁版本
     */
    void onSaved(Object identity, Object aggregate, Long newVersion);
}
