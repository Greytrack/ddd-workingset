package com.airedgesoft.lib.restful.etag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemoryEtagHashCache} 有界 LRU 契约：key 规模随资源 × 查询组合增长，超上限淘汰最久未用条目，杜绝无界内存增长。
 */
class InMemoryEtagHashCacheTest {

    @AfterEach
    void reset() {
        EtagContext.clear();
    }

    @Test
    void evictsLeastRecentlyUsedBeyondLimit() {
        InMemoryEtagHashCache cache = new InMemoryEtagHashCache(2);
        cache.put("k1", "h1");
        cache.put("k2", "h2");
        cache.get("k1"); // k1 变为最近使用
        cache.put("k3", "h3");

        assertThat(cache.get("k1")).contains("h1");
        assertThat(cache.get("k2")).isEmpty();
        assertThat(cache.get("k3")).contains("h3");
    }

    @Test
    void defaultLimitIsBounded() {
        InMemoryEtagHashCache cache = new InMemoryEtagHashCache();
        for (int i = 0; i < InMemoryEtagHashCache.DEFAULT_MAX_ENTRIES + 100; i++) {
            cache.put("k" + i, "h" + i);
        }
        assertThat(cache.get("k0")).isEmpty();
        assertThat(cache.get("k" + (InMemoryEtagHashCache.DEFAULT_MAX_ENTRIES + 99)))
                .contains("h" + (InMemoryEtagHashCache.DEFAULT_MAX_ENTRIES + 99));
    }
}
