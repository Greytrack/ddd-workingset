package com.airedgesoft.lib.restful.etag;

import java.util.Optional;

/**
 * 表示 hash 缓存端口（{@code ETag} 只做缓存校验时的可选存储）。
 *
 * <p>
 * 默认实现为进程内缓存（{@link InMemoryEtagHashCache}）；宿主提供 Redis 实现时以同型 Bean 覆盖。缓存 <b>只存元数据 hash</b>，不存响应体；Redis
 * 故障一律降级为「无缓存」，绝不放行错误的 304。
 */
public interface EtagHashCache {

    /** 读取表示 hash（未命中 → empty）。 */
    Optional<String> get(String key);

    /** 写入表示 hash（失败不得影响正常 200 响应）。 */
    void put(String key, String hash);
}
