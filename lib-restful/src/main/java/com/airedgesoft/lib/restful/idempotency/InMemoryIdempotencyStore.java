package com.airedgesoft.lib.restful.idempotency;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link IdempotencyStore} 的内存实现（<b>本地 H2 workshop 兜底</b>：无 Redis 运行时自动装配此实现；生产默认走 {@link RedissonIdempotencyStore}，见
 * {@link IdempotencyAutoConfiguration}。契约不变）。
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24L);
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap();

    @Override
    public CachedResponse begin(String namespace, String key, String requestBody) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String storeKey = storeKey(namespace, key);
        String fingerprint = sha256(requestBody);
        Instant now = Instant.now();
        Entry[] created = new Entry[1];
        Entry existing = store.compute(storeKey, (k, cur) -> {
            if (cur != null && cur.expiresAt().isAfter(now)) {
                return cur;
            }
            created[0] = new Entry(fingerprint, null, now.plus(TTL));
            return created[0];
        });
        if (created[0] != null) {
            return null;
        }
        if (!existing.requestFingerprint().equals(fingerprint)) {
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
        store.computeIfPresent(storeKey(namespace, key),
                (k, cur) -> new Entry(cur.requestFingerprint(), response, cur.expiresAt()));
    }

    @Override
    public void release(String namespace, String key) {
        if (key != null && !key.isBlank()) {
            store.remove(storeKey(namespace, key));
        }
    }

    private static String storeKey(String namespace, String key) {
        return (namespace == null || namespace.isBlank() ? "" : namespace + ":") + key;
    }

    private static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((data == null ? "" : data).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private record Entry(String requestFingerprint, CachedResponse response, Instant expiresAt) {
    }
}
