package com.airedgesoft.lib.restful.idempotency;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * {@link IdempotencyStore} 的 Redis 实现（默认，经 {@link IdempotencyAutoConfiguration} 在宿主具备 Redisson 时自动生效；控制层零改动）。
 *
 * <p>
 * <b>存储</b>：{@code airedge:idempotency:{namespace}:{clientKey}} → JSON {@code {fingerprint, expiresAtMillis,
 * response?}}，按 24h TTL 惰性清理。 <b>语义</b>：三阶段 PROCESSING（占位）→ COMPLETED（缓存响应快照回放）→ 过期；同 key 异 body → 422；处理中并发 →
 * 409；失败不缓存、异常释放 key。 响应 body 以 JSON 序列化持久化，回放时反序列化为通用对象（Spring 再序列化等价 JSON；{@code @JsonInclude(NON_NULL)} 已先期应用）。
 *
 * <p>
 * <b>原子性</b>：占用用 {@code trySet(value, ttl)}（set-if-absent，原子）；过期占位交接与完成写回用 {@code compareAndSet(expect, update, ttl)}
 * （CAS，原子）——多实例并发下同样成立，契合"处理中 → 409 / 已完成 → 回放"契约。
 */
public class RedissonIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "airedge:idempotency:";
    private static final Duration TTL = Duration.ofHours(24L);

    private final RedissonClient client;
    private final ObjectMapper objectMapper;

    public RedissonIdempotencyStore(RedissonClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public CachedResponse begin(String namespace, String key, String requestBody) {
        if (key == null || key.isBlank()) {
            return null;
        }
        RBucket<String> bucket = bucket(namespace, key);
        String fingerprint = sha256(requestBody);
        String nowJson = bucket.get();
        if (nowJson == null) {
            // 首次见：set-if-absent 原子占用（并发竞争时只有一个成功）
            boolean claimed = bucket.trySet(entryJson(newEntry(fingerprint, null, now() + TTL.toMillis())),
                    TTL.toMillis(), TimeUnit.MILLISECONDS);
            if (claimed) {
                return null;
            }
            nowJson = bucket.get();
        }
        Entry existing = parse(nowJson);
        if (expired(existing)) {
            // 过期：CAS 交接重占（其余并发失败者回到 409/422 判定）
            String fresh = entryJson(newEntry(fingerprint, null, now() + TTL.toMillis()));
            if (bucket.compareAndSet(nowJson, fresh)) {
                return null;
            }
            existing = parse(bucket.get());
        }
        if (!existing.fingerprint().equals(fingerprint)) {
            throw new DomainException(DomainRestCode.IDEMPOTENCY_PAYLOAD_MISMATCH);
        }
        if (existing.response() == null) {
            throw new DomainException(DomainRestCode.IDEMPOTENCY_IN_FLIGHT);
        }
        return existing.response();
    }

    @Override
    public void complete(String namespace, String key, CachedResponse response) {
        if (key == null || key.isBlank()) {
            return;
        }
        RBucket<String> bucket = bucket(namespace, key);
        String nowJson = bucket.get();
        if (nowJson == null) {
            return;
        }
        Entry existing = parse(nowJson);
        if (existing.response() != null) {
            return; // 已回放完成，幂等覆盖无意义
        }
        long remaining = existing.expiresAtMillis() - now();
        if (remaining <= 0) {
            bucket.delete();
            return;
        }
        Entry updated = newEntry(existing.fingerprint(), response, existing.expiresAtMillis());
        if (bucket.compareAndSet(nowJson, entryJson(updated))) {
            bucket.expire(remaining, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void release(String namespace, String key) {
        if (key != null && !key.isBlank()) {
            bucket(namespace, key).delete();
        }
    }

    private RBucket<String> bucket(String namespace, String key) {
        return client.getBucket(KEY_PREFIX + storeKey(namespace, key), StringCodec.INSTANCE);
    }

    private static String storeKey(String namespace, String key) {
        return (namespace == null || namespace.isBlank() ? "" : namespace + ":") + key;
    }

    // ─── Entry 序列化 ───

    private Entry newEntry(String fingerprint, CachedResponse response, long expiresAtMillis) {
        return new Entry(fingerprint, response, expiresAtMillis);
    }

    private String entryJson(Entry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fingerprint", entry.fingerprint());
        map.put("expiresAtMillis", entry.expiresAtMillis());
        map.put("response", entry.response() == null ? null : serialize(entry.response()));
        return write(map);
    }

    private Entry parse(String json) {
        JsonNode node = read(json);
        String fingerprint = node.path("fingerprint").asText(null);
        long expires = node.path("expiresAtMillis").asLong(0);
        CachedResponse response = node.has("response") && !node.get("response").isNull()
                ? deserialize(node.get("response"))
                : null;
        return new Entry(fingerprint, response, expires);
    }

    /** 响应快照 → JSON 节点（body 为任意 DTO，经 ObjectMapper 树化持久化）。 */
    private JsonNode serialize(CachedResponse response) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", response.status());
        map.put("location", response.location());
        map.put("eTag", response.eTag());
        map.put("headers", response.headers());
        map.put("body", response.body());
        return objectMapper.valueToTree(map);
    }

    /** JSON 节点 → 响应快照（body 反序列化为通用对象，Spring 再序列化等价 JSON）。 */
    private CachedResponse deserialize(JsonNode responseNode) {
        int status = responseNode.path("status").asInt(200);
        String location = responseNode.path("location").isNull() ? null : responseNode.path("location").asText(null);
        String eTag = responseNode.path("eTag").isNull() ? null : responseNode.path("eTag").asText(null);
        Object body = responseNode.has("body") && !responseNode.get("body").isNull()
                ? objectMapper.convertValue(responseNode.get("body"), Object.class)
                : null;
        java.util.Map<String, String> headers = responseNode.has("headers") && !responseNode.get("headers").isNull()
                ? objectMapper.convertValue(responseNode.get("headers"),
                        objectMapper.getTypeFactory().constructMapType(java.util.LinkedHashMap.class, String.class,
                                String.class))
                : null;
        return new CachedResponse(status, location, eTag, body, headers);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("幂等条目序列化失败", e);
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("幂等条目反序列化失败", e);
        }
    }

    private static boolean expired(Entry entry) {
        return entry.expiresAtMillis() <= Instant.now().toEpochMilli();
    }

    private static long now() {
        return Instant.now().toEpochMilli();
    }

    private static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((data == null ? "" : data).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private record Entry(String fingerprint, CachedResponse response, long expiresAtMillis) {
    }
}
