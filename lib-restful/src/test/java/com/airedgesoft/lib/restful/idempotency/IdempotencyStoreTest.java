package com.airedgesoft.lib.restful.idempotency;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等键状态机契约单测（规范 §6）：占位 / 回放 / 422 指纹不符 / 409 处理中 / 失败释放； 命名空间按 operationId 隔离（同 key 跨端点互不干扰）。
 */
class IdempotencyStoreTest {
    // 契约行为断言对实现类执行；接口语义由规范 §6 锚定

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    @Test
    void begin_firstSeen_returnsNullThenCompleteReplays() {
        assertThat(store.begin("xCreate", "k1", "body-A")).isNull();
        store.complete("xCreate", "k1", new IdempotencyStore.CachedResponse(201, "/v1/x/1", "\"sha256-test\"", "resp"));
        IdempotencyStore.CachedResponse replay = store.begin("xCreate", "k1", "body-A");
        assertThat(replay).isNotNull();
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo("resp");
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
        assertThat(store.begin("xCreate", "k3", "body")).isNull(); // 占位未 complete
        assertThatThrownBy(() -> store.begin("xCreate", "k3", "body")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.CONFLICT);
    }

    @Test
    void release_allowsRetryWithSameKey() {
        assertThat(store.begin("xCreate", "k4", "body")).isNull();
        store.release("xCreate", "k4");
        assertThat(store.begin("xCreate", "k4", "body")).isNull(); // 释放后可重新占位
    }

    @Test
    void namespace_isolatedPerOperation() {
        store.begin("aCreate", "shared-key", "body-A");
        // 同 key 在另一端点首见：不受 aCreate 占位影响
        assertThat(store.begin("bCreate", "shared-key", "body-B")).isNull();
    }

    @Test
    void blankKey_bypassesDeduplication() {
        assertThat(store.begin("xCreate", null, "body")).isNull();
        assertThat(store.begin("xCreate", " ", "body")).isNull();
    }
}
