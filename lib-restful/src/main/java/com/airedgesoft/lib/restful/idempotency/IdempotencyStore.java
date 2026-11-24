package com.airedgesoft.lib.restful.idempotency;

/**
 * 幂等键存储<b>端口</b>（规范 §6 的契约载体；机制思想：Geewax ch26 Request deduplication， 承载形式：IETF {@code Idempotency-Key} 头 = 有意偏离，见规范
 * §11.3）。
 *
 * <p>
 * <b>实现策略（v4-restful-api.md §6，2026-10-09 定为 Redis）</b>：
 * <ul>
 * <li>默认实现 {@link RedissonIdempotencyStore}（Redis，deploy-full 经 legacy {@code infrastructure-tools} 的 Redisson
 * 自动生效）。</li>
 * <li>{@link InMemoryIdempotencyStore} 仅作本地 H2 workshop 兜底（无 Redis 运行时）。</li>
 * <li><b>不引第三方幂等 starter</b>：{@code pig-mesh/idempotent-spring-boot-starter} 的语义是 "ip+url+args 指纹、TTL
 * 内报错拒绝"，<b>无响应回放</b>且 key 非客户端 UUID，与本契约（回放 + 客户端 key）不匹配； {@code transferwise/idempotence4j} 需要 DB 持久化注册表 + 结果序列化且不含
 * HTTP 层，适合"跨重启可恢复"的进阶需求。</li>
 * </ul>
 *
 * <p>
 * <b>契约</b>：三阶段流转 PROCESSING（占位，并发重复 → 409）→ COMPLETED（缓存响应快照回放）→ 过期； 同 key 异 body → 422【偏离声明】；失败不缓存、异常释放 key； store
 * key = {@code namespace + ":" + clientKey}（命名空间由 {@link IdempotencyFilter} 派生为 {@code POST {requestURI}}，按端点 +
 * 实例隔离——下方 {@code namespace} 形参即承载该命名空间）。 回放由 {@link IdempotencyFilter}（Servlet Filter，非切面）把快照<b>写回
 * HttpServletResponse</b>（状态码 + Location + ETag + 附加头（Vary / Cache-Control）+ body 字节，逐字节等价）。
 */
public interface IdempotencyStore {

    /**
     * 幂等键 + 请求指纹核验（按命名空间隔离，namespace = {@code POST {requestURI}}）：首见返回 {@code null}（调用方执行业务）； 已完成 → 回放缓存响应快照；处理中 →
     * 409（CONFLICT）；指纹不符 → 422（UNPROCESSABLE_ENTITY）。
     */
    CachedResponse begin(String namespace, String key, String requestBody);

    /** 业务成功后缓存响应快照（状态码 + Location + ETag + Body），供重试回放。 */
    void complete(String namespace, String key, CachedResponse response);

    /** 业务异常时释放占位（失败不缓存，允许同 key 重试；与 IETF 草案一致）。 */
    void release(String namespace, String key);

    /**
     * 响应快照：body 即端点声明的返回 DTO（同端点缓存，类型必然匹配）。{@code headers} 承载需随回放还原的附加响应头 （如 {@code @EnableRepresentationCache} 端点的
     * {@code Vary} / {@code Cache-Control}）；Location / ETag 单列保留以兼容旧快照。
     */
    record CachedResponse(int status, String location, String eTag, Object body,
            java.util.Map<String, String> headers) {

        public CachedResponse(int status, String location, String eTag, Object body) {
            this(status, location, eTag, body, null);
        }
    }
}
