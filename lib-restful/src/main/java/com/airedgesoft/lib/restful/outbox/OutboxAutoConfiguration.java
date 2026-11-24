package com.airedgesoft.lib.restful.outbox;

import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Outbox 自动装配（Transactional Outbox；Tier D 基建）。
 *
 * <p>
 * <b>装配内容</b>：宿主具备 {@link JdbcTemplate} 时注册 {@link JdbcOutboxStore}（表 {@code airedge_outbox}，DDL 见
 * {@code classpath:schema/outbox-{h2,postgresql}.sql}）；{@code airedge.restful.outbox.auto-start=true} 时注册
 * {@link OutboxDispatcher} 并<b>随容器生命周期启停</b>（Bean 初始化即 {@code start()}，容器关闭 {@code stop()}；首轮轮询延迟一个间隔，缺省
 * 2s）。<b>发布器必须由容器提供</b>（{@code Consumer<OutboxRecord>} Bean）——缺失即启动失败（fail-fast），不做 no-op 兜底：空发布器会把事件
 * {@code markPublished} 后静默丢弃。缺省 {@code auto-start=false}，生产接入由 BC 自管 Dispatcher Bean 并显式 start（发布器是 BC 业务语义）。
 *
 * <p>
 * <b>排序</b>：{@code @ConditionalOnBean(JdbcTemplate)} 依赖 {@link JdbcTemplateAutoConfiguration} 先行注册——
 * 自动装配按字母序排列时本类（{@code com.airedgesoft.*}）先于 {@code org.springframework.boot.jdbc.*} 求值，必须显式声明 After， 否则 OutboxStore
 * 被静默跳过、宿主启动报 {@code OutboxStore could not be found}。
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(OutboxStore.class)
    public OutboxStore outboxStore(JdbcTemplate jdbcTemplate) {
        return new JdbcOutboxStore(jdbcTemplate);
    }

    /**
     * 单机快速接入形态：{@code auto-start=true} 时装配并启动轮询（batchSize=50、间隔 2s，发布器取容器中的 {@code Consumer<OutboxRecord>} Bean，缺失即
     * fail-fast）。BC 自管形态：BC 声明本类型 Bean 后本装配退让，启停由 BC 决定。
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnBean(OutboxStore.class)
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    @ConditionalOnProperty(prefix = "airedge.restful.outbox", name = "auto-start", havingValue = "true")
    public OutboxDispatcher outboxDispatcher(OutboxStore store, ObjectProvider<Consumer<OutboxRecord>> publisher) {
        Consumer<OutboxRecord> resolved = publisher.getIfAvailable();
        if (resolved == null) {
            throw new IllegalStateException(
                    "airedge.restful.outbox.auto-start=true 需要容器提供 Consumer<OutboxRecord> 发布器 Bean："
                            + "无发布器时轮询会把事件标记已发布后丢弃；请装配发布器，或关闭 auto-start 改由 BC 自管 OutboxDispatcher");
        }
        return new OutboxDispatcher(store, resolved, 50, Duration.ofSeconds(2));
    }
}
