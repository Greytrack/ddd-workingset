package com.airedgesoft.lib.restful.etag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link EtagHashCache} 的进程内默认实现（仅用于未配置 Redis 的宿主）。
 *
 * <p>
 * <b>有界 LRU</b>：key 含 URI + 查询串 + 授权上下文哈希，规模随「资源 × 查询组合 × 用户」增长而非端点数，故缺省上限 {@value #DEFAULT_MAX_ENTRIES}
 * 条、按访问序淘汰最久未用条目，杜绝无界内存增长。需要 TTL / 跨实例共享时由宿主提供 Redis 实现覆盖本 Bean。
 *
 * <p>
 * 当前 304 判定直接比较本次表示 hash 与 {@code If-None-Match}，本缓存为<b>写侧登记</b>（供诊断与未来跨实例实现），不参与 304 决策。
 */
public class InMemoryEtagHashCache implements EtagHashCache {

    public static final int DEFAULT_MAX_ENTRIES = 4096;

    private final Map<String, String> hashes;

    public InMemoryEtagHashCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryEtagHashCache(int maxEntries) {
        int limit = maxEntries <= 0 ? DEFAULT_MAX_ENTRIES : maxEntries;
        this.hashes = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > limit;
            }
        });
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(hashes.get(key));
    }

    @Override
    public void put(String key, String hash) {
        if (key != null && hash != null) {
            hashes.put(key, hash);
        }
    }
}
