package com.airedgesoft.lib.restful.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox 自动装配契约：{@code auto-start=true} 必须真正启动轮询并随容器停止；发布器缺失 fail-fast（不做 no-op 兜底， 否则事件被标记已发布后静默丢弃）；BC 自管 Dispatcher
 * Bean 时装配退让、不代管启停。
 */
class OutboxAutoConfigurationTest {

    private static final List<String> PUBLISHED = new CopyOnWriteArrayList<>();

    @BeforeEach
    void resetPublished() {
        PUBLISHED.clear();
    }

    @Test
    void autoStartDisabled_registersNoDispatcher() {
        try (AnnotationConfigApplicationContext ctx = context(false, true)) {
            ctx.refresh();
            assertThat(ctx.getBean(OutboxStore.class)).isNotNull();
            assertThat(ctx.getBeanNamesForType(OutboxDispatcher.class)).isEmpty();
        }
    }

    @Test
    void autoStartEnabled_pollsAndStopsWithContainer() {
        AnnotationConfigApplicationContext ctx = context(true, true);
        ctx.refresh();
        MemoryStore store = (MemoryStore) ctx.getBean(OutboxStore.class);
        OutboxDispatcher dispatcher = ctx.getBean(OutboxDispatcher.class);
        assertThat(dispatcher.isRunning()).isTrue();

        store.append(OutboxRecord.pending("1", "demo.task.activated", "demoTasks", "101", "{}"));
        awaitPublished("1"); // 首轮轮询延迟一个间隔（2s），证明 auto-start 真的在派发

        ctx.close();
        assertThat(dispatcher.isRunning()).isFalse();
    }

    @Test
    void autoStartEnabled_withoutPublisher_failsFast() {
        AnnotationConfigApplicationContext ctx = context(true, false);
        assertThatThrownBy(ctx::refresh).hasStackTraceContaining("Consumer<OutboxRecord> 发布器 Bean");
        ctx.close();
    }

    @Test
    void bcManagedDispatcher_autoConfigurationBacksOff() {
        AnnotationConfigApplicationContext ctx = context(true, true, BcDispatcherConfig.class);
        ctx.refresh();
        try (ctx) {
            OutboxDispatcher dispatcher = ctx.getBean(OutboxDispatcher.class);
            assertThat(dispatcher.isRunning()).isFalse(); // BC 自管：装配不代为 start
            dispatcher.start();
            assertThat(dispatcher.isRunning()).isTrue();
        }
    }

    private static void awaitPublished(String id) {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (!PUBLISHED.contains(id) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(PUBLISHED).contains(id);
    }

    private static AnnotationConfigApplicationContext context(boolean autoStart, boolean withPublisher,
            Class<?>... bcConfigurations) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        if (autoStart) {
            ctx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("outbox-test", Map.of("airedge.restful.outbox.auto-start", "true")));
        }
        ctx.register(StoreConfig.class);
        if (withPublisher) {
            ctx.register(PublisherConfig.class);
        }
        // 模拟 Boot 的自动装配后置顺序：BC 配置先注册，@ConditionalOnMissingBean 才看得到 BC 的 Dispatcher Bean
        if (bcConfigurations.length > 0) {
            ctx.register(bcConfigurations);
        }
        ctx.register(OutboxAutoConfiguration.class);
        return ctx;
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreConfig {

        @Bean
        OutboxStore outboxStore() {
            return new MemoryStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PublisherConfig {

        @Bean
        Consumer<OutboxRecord> outboxPublisher() {
            return record -> PUBLISHED.add(record.id());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BcDispatcherConfig {

        @Bean
        OutboxDispatcher bcOutboxDispatcher(OutboxStore store) {
            return new OutboxDispatcher(store, record -> PUBLISHED.add(record.id()), 10, Duration.ofSeconds(1));
        }
    }

    /** 内存存根（fetch 升序、markPublished 幂等）。 */
    private static final class MemoryStore implements OutboxStore {

        private final List<OutboxRecord> records = new ArrayList<>();

        @Override
        public synchronized void append(OutboxRecord record) {
            records.add(record);
        }

        /** 使用与 JDBC 派发相同的时间和主键顺序。 */
        @Override
        public synchronized List<OutboxRecord> fetchUnpublished(int limit, OutboxRecord after, OutboxRecord through) {
            var order = java.util.Comparator.comparingLong(OutboxRecord::createTimeMs).thenComparing(OutboxRecord::id);
            return records.stream().filter(record -> !record.published())
                    .filter(record -> after == null || order.compare(record, after) > 0)
                .filter(record -> through == null || order.compare(record, through) <= 0)
                    .sorted(order).limit(limit).toList();
        }

        /** 定位当前未发布队尾。 */
        @Override
        public synchronized Optional<OutboxRecord> findLastUnpublished() {
            var order = java.util.Comparator.comparingLong(OutboxRecord::createTimeMs).thenComparing(OutboxRecord::id);
            return records.stream().filter(record -> !record.published()).max(order);
        }

        @Override
        public synchronized void markPublished(String id) {
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).id().equals(id)) {
                    records.set(i, records.get(i).asPublished());
                }
            }
        }
    }
}
