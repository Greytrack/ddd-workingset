package com.airedgesoft.lib.restful.lro;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * LRO 存储选择契约（与幂等组件同型）：宿主具备 Redisson 客户端 → Redis；无客户端 / 无类路径 → 内存兜底。
 */
class LroStoreSelectionTest {

    @Test
    void withoutRedissonClient_fallsBackToInMemory() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(LroRedissonAutoConfiguration.class);
            ctx.refresh();
            assertThat(ctx.getBean(OperationStore.class)).isInstanceOf(InMemoryOperationStore.class);
        }
    }

    @Test
    void withRedissonClient_usesRedisStore() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            Supplier<RedissonClient> client = () -> mock(RedissonClient.class);
            ctx.registerBean(RedissonClient.class, client);
            ctx.register(LroRedissonAutoConfiguration.class); // @EnableConfigurationProperties 自带 LroProperties
            ctx.refresh();
            assertThat(ctx.getBean(OperationStore.class)).isInstanceOf(RedissonOperationStore.class);
        }
    }
}
