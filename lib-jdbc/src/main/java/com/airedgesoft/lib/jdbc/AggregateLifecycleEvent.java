package com.airedgesoft.lib.jdbc;

/**
 * 聚合持久化生命周期事件：一次加载或保存完成后交给 {@link AggregateLifecycleListener} 的技术信号。
 *
 * <p>
 * <b>不是领域事件</b>：本类型描述持久化流程节点（重建 / 插入 / 更新），不承载业务语义；lib-jdbc 不依赖任何 BC 与领域类型， 监听方按 {@link #mapping()} 认领自己关心的聚合。
 *
 * <p>
 * <b>触发时机</b>：{@link Kind#LOADED} 在聚合从存储快照重建完成时触发（每次加载）；{@link Kind#CREATED} 与 {@link Kind#UPDATED} 仅在实际执行了写入 SQL
 * 时触发——字段无净变化（{@code Receipt.Unchanged}）不产生事件。 全部回调都发生在外层写事务内、提交之前；回调返回不代表事务已提交。
 *
 * @param kind
 *            本次生命周期节点；非空
 * @param mapping
 *            产生本次事件的聚合映射实例，即监听方识别来源的依据——仓储在同一份不可变映射上复用，故引用相等即「同一仓储」； 注意 {@code toBuilder()} 派生的实例不等于原实例
 * @param identity
 *            聚合身份；可能为空（新建尚未分配身份时）
 * @param aggregate
 *            重建出或已保存的聚合对象
 * @param version
 *            加载时为原始存储版本（null 表示未配置或旧库 NULL 版本）；新增与更新为保存后推进的最新锁版本
 */
public record AggregateLifecycleEvent(Kind kind, AggregateMapping<?, ?> mapping, Object identity, Object aggregate,
        Long version) {

    /**
     * 生命周期节点种类。新增节点时在枚举末尾追加，并在触发点补齐分派。
     */
    public enum Kind {
        /** 聚合从存储快照重建完成；每次加载触发（含写事务内的首次装配）。 */
        LOADED,
        /** 本次保存实际执行了 INSERT。 */
        CREATED,
        /** 本次保存实际执行了 UPDATE（含根版本 CAS）。 */
        UPDATED
    }

    /**
     * 校验事件具备节点种类、来源映射与聚合对象。
     *
     * @param kind
     *            非空的生命周期节点
     * @param mapping
     *            非空的来源聚合映射
     * @param identity
     *            聚合身份；允许为空
     * @param aggregate
     *            非空的聚合对象
     * @param version
     *            锁版本；允许为空
     */
    public AggregateLifecycleEvent {
        if (kind == null || mapping == null || aggregate == null) {
            throw new IllegalArgumentException("聚合生命周期事件不完整");
        }
    }
}
