package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.jdbc.AggregateLifecycleEvent;
import com.airedgesoft.lib.jdbc.AggregateLifecycleListener;
import com.airedgesoft.lib.jdbc.JdbcAggregateStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 将 {@link JdbcAggregateStore} 的聚合持久化事件自动桥接到 {@link EtagContext}。
 *
 * <p>
 * 自注册 Bean：装配为 Spring Bean 即在初始化时挂入全局监听表、销毁时注销（注册按实例身份去重，宿主保持单例实例即不累积重复触发）。
 *
 * <p>
 * <b>加载期为何走 {@link EtagContext#recordLoaded}（带版本断言）而 Spring Data JDBC 桥接
 * （{@link EtagEntityLifecycleCallback}）只观测</b>：两条写模型的并发保护承载点不同——
 * <ul>
 * <li>{@code JdbcAggregateStore} 路径的 CAS 基线由 store 事务上下文自持，<b>没有</b> {@code @Version} 谓词承载客户端期望， 版本
 * 的唯一校验点就是本监听器的加载期断言（不一致 → 409，写入前生效）；</li>
 * <li>Spring Data JDBC 路径由 {@code @Version} CAS 与写映射器登记点断言承载，实体回调只观测（跨表同值主键防误 409）。</li>
 * </ul>
 * 断言抛出的 409 会使当次事务回滚（store 上下文随之失效被丢弃），不产生「写后报错」；跨集合同值 id 的误判风险由标识规范（§2 雪花 ID 全局唯一）兜底。
 */
public class EtagAggregateLifecycleListener implements AggregateLifecycleListener {

    @PostConstruct
    public void attach() {
        JdbcAggregateStore.registerListener(this);
    }

    @PreDestroy
    public void retract() {
        JdbcAggregateStore.unregisterListener(this);
    }

    @Override
    public void onEvent(AggregateLifecycleEvent event) {
        // 加载期断言（版本不一致即 409 并在写入前回滚）；写入期只登记保存后的新代数。
        switch (event.kind()) {
            case LOADED -> EtagContext.recordLoaded(event.identity(), event.aggregate(), event.version());
            case CREATED, UPDATED -> EtagContext.recordSaved(event.identity(), event.aggregate(), event.version());
        }
    }
}
