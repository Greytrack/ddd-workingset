package com.airedgesoft.lib.restful.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 幂等存储默认选择契约（v4-restful-api.md §6）：宿主具备 Redisson 客户端 → {@link RedissonIdempotencyStore}（Redis，默认）； 无 Redisson（本地 H2
 * workshop）→ {@link InMemoryIdempotencyStore} 兜底。
 */
class IdempotencyAutoConfigurationTest {

    @Test
    void withoutRedissonClient_fallsBackToInMemory() {
        try (AnnotationConfigApplicationContext ctx = context(false)) {
            assertThat(ctx.getBean(IdempotencyStore.class)).isInstanceOf(InMemoryIdempotencyStore.class);
        }
    }

    @Test
    void withRedissonClient_usesRedisStore() {
        try (AnnotationConfigApplicationContext ctx = context(true)) {
            assertThat(ctx.getBean(IdempotencyStore.class)).isInstanceOf(RedissonIdempotencyStore.class);
        }
    }

    private static AnnotationConfigApplicationContext context(boolean withRedis) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        if (withRedis) {
            Supplier<RedissonClient> client = () -> mock(RedissonClient.class);
            Supplier<ObjectMapper> objectMapper = ObjectMapper::new;
            ctx.registerBean(RedissonClient.class, client);
            ctx.registerBean(ObjectMapper.class, objectMapper);
        }
        ctx.register(RedissonIdempotencyAutoConfiguration.class, IdempotencyAutoConfiguration.class);
        ctx.refresh();
        return ctx;
    }
}
