package com.airedgesoft.lib.restful.idempotency;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RedissonIdempotencyStore} 契约单测：以内存 Map 模拟 RBucket 语义（get / trySet / compareAndSet / delete）， 断言与
 * {@link IdempotencyStoreTest}（内存实现）一致的 §6 状态机（占位 / 回放 / 422 / 409 / 释放），验证 Redis 实现契约等价。
 */
class RedissonIdempotencyStoreTest {

    private final Map<String, String> redis = new ConcurrentHashMap<>();
    private RedissonIdempotencyStore store;

    @BeforeEach
    void setUp() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getBucket(anyString(), any())).thenAnswer(inv -> fakeBucket(inv.getArgument(0)));
        store = new RedissonIdempotencyStore(client, new ObjectMapper());
    }

    private RBucket<String> fakeBucket(String name) {
        RBucket<String> bucket = mock(RBucket.class);
        when(bucket.get()).thenAnswer(inv -> redis.get(name));
        when(bucket.trySet(anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> redis.putIfAbsent(name, inv.getArgument(0)) == null);
        when(bucket.compareAndSet(anyString(), anyString())).thenAnswer(inv -> {
            String expect = inv.getArgument(0);
            String update = inv.getArgument(1);
            if (expect.equals(redis.get(name))) {
                redis.put(name, update);
                return true;
            }
            return false;
        });
        when(bucket.expire(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(bucket.delete()).thenAnswer(inv -> redis.remove(name) != null);
        return bucket;
    }

    @Test
    void begin_firstSeen_returnsNullThenCompleteReplays() {
        assertThat(store.begin("xCreate", "k1", "body-A")).isNull();
        store.complete("xCreate", "k1",
                new IdempotencyStore.CachedResponse(201, "/v1/x/1", "\"sha256-test\"", Map.of("id", 1)));
        IdempotencyStore.CachedResponse replay = store.begin("xCreate", "k1", "body-A");
        assertThat(replay).isNotNull();
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.location()).isEqualTo("/v1/x/1");
        assertThat(replay.eTag()).isEqualTo("\"sha256-test\"");
        assertThat(replay.body()).isInstanceOf(Map.class);
    }

    @Test
    void begin_sameKeyDifferentBody_throws422() {
        store.begin("xCreate", "k2", "body-A");
        store.complete("xCreate", "k2", new IdempotencyStore.CachedResponse(201, null, null, "r"));
        assertThatThrownBy(() -> store.begin("xCreate", "k2", "body-B")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.UNPROCESSABLE_ENTITY);
    }

    @Test
    void begin_inFlight_throws409() {
        assertThat(store.begin("xCreate", "k3", "body")).isNull();
        assertThatThrownBy(() -> store.begin("xCreate", "k3", "body")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.CONFLICT);
    }

    @Test
    void release_allowsRetryWithSameKey() {
        assertThat(store.begin("xCreate", "k4", "body")).isNull();
        store.release("xCreate", "k4");
        assertThat(store.begin("xCreate", "k4", "body")).isNull();
    }

    @Test
    void blankKey_bypassesDeduplication() {
        assertThat(store.begin("xCreate", null, "body")).isNull();
        assertThat(store.begin("xCreate", " ", "body")).isNull();
    }
}
