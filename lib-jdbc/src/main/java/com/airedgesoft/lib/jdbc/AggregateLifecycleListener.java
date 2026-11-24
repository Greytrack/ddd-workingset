package com.airedgesoft.lib.jdbc;

/**
 * 聚合持久化生命周期监听器：解耦底层增量写入技术与上层协议/观测（如 ETag、审计、缓存一致性）。
 *
 * <p>
 * <b>全局语义</b>：经 {@link JdbcAggregateStore#registerListener} 注册一次即对全部 Store 实例生效，回调收全部聚合； 监听方按
 * {@link AggregateLifecycleEvent#mapping()} 或 {@code aggregate} 类型自行认领。按 Store 分域的注册域不提供。
 *
 * <p>
 * <b>注册按实例身份去重</b>：同一实例重复注册幂等；一个实现类可以注册多个不同实例，互不顶替。因此<b>不要覆写 {@code equals} /
 * {@code hashCode}</b>——若覆写，去重将按其自定义等价语义而非实例身份进行。
 *
 * <p>
 * <b>生命周期约定</b>：宿主应注册与自身生命周期一致的<b>单例实例</b>，并在销毁时调用 {@link JdbcAggregateStore#unregisterListener} 注销（如 Spring Bean 的
 * {@code @PostConstruct} 注册 / {@code @PreDestroy} 注销）。<b>每次注册都新建实例会造成累积重复触发</b>——按实现类顶替的旧保护已随实例键取消。
 *
 * <p>
 * <b>顺序与异常</b>：监听器之间<b>不保证调用顺序</b>，不得依赖彼此的先后；监听器抛出的异常照旧向上传播，使本次写入失败并标记事务回滚 （ETag 的加载期版本断言依赖这一行为）。
 *
 * <p>
 * <b>触发时机</b>：回调发生在外层写事务内、提交之前。监听器内的外部副作用（发消息、调远端、写缓存）可能随事务回滚而失效， 不应据此认定业务已持久化。
 */
public interface AggregateLifecycleListener {

    /**
     * 聚合生命周期节点回调。
     *
     * @param event
     *            非空的生命周期事件；其中 {@link AggregateLifecycleEvent#kind()} 表示节点种类， {@link AggregateLifecycleEvent#mapping()}
     *            为来源聚合映射实例
     */
    void onEvent(AggregateLifecycleEvent event);
}
