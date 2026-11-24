package com.airedgesoft.lib.restful.etag;

import static org.assertj.core.api.Assertions.assertThat;

import com.airedgesoft.lib.jdbc.AggregateLifecycleEvent;
import com.airedgesoft.lib.jdbc.AggregateLifecycleEvent.Kind;
import com.airedgesoft.lib.jdbc.AggregateMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 桥接分派单测：{@code AggregateLifecycleEvent.Kind} → {@link EtagContext} 两个登记点的映射。
 *
 * <p>
 * 现有 ETag 用例全部直调 {@code EtagContext}，不经 lib-jdbc 注册路径；本用例补上「事件种类 → 登记入口」这一层， 使桥接改动有回归保护（不依赖 Spring 容器与数据库）。
 */
class EtagAggregateLifecycleListenerTest {

    private static final AggregateMapping<Object, Long> MAPPING = AggregateMapping.<Object, Long>builder()
            .identity(instance -> 1L).storedIdentity(row -> row.root().longValue("entry_key"))
            .onLoad(row -> new Object()).build();

    @AfterEach
    void reset() {
        EtagContext.clear();
    }

    private static AggregateLifecycleEvent event(Kind kind, Object instance, Long version) {
        return new AggregateLifecycleEvent(kind, MAPPING, 1L, instance, version);
    }

    @Test
    void onEvent_loadedKindRecordsLoadedBaseline() {
        Object instance = new Object();

        new EtagAggregateLifecycleListener().onEvent(event(Kind.LOADED, instance, 4L));

        // 加载期登记加载版本与当前代数（后者供响应体 ETag 组装）；无期望绑定时不抛 409。
        assertThat(EtagContext.currentStorageVersion("1")).isEqualTo(4L);
    }

    @Test
    void onEvent_createdKindRecordsSaved() {
        Object instance = new Object();

        new EtagAggregateLifecycleListener().onEvent(event(Kind.CREATED, instance, 0L));

        assertThat(EtagContext.currentStorageVersion("1")).isEqualTo(0L);
    }

    @Test
    void onEvent_updatedKindRecordsSaved() {
        Object instance = new Object();

        new EtagAggregateLifecycleListener().onEvent(event(Kind.UPDATED, instance, 7L));

        assertThat(EtagContext.currentStorageVersion("1")).isEqualTo(7L);
    }
}
