package com.airedgesoft.lib.restful.lro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * {@link OperationStore} Redisson 实现（多实例共享轮询状态；宿主具备 {@code RedissonClient} 时由 {@code LroRedissonAutoConfiguration}
 * 优先装配）。键空间 {@code airedge:restful:lro:operation:{operationId}}，TTL = {@link #ttl}（终态数据到期自动清理）。
 *
 * <p>
 * 序列化经 Jackson（JSON 字符串入 {@link RBucket}），与幂等组件同一 ObjectMapper 注入约定；终态一致以整对象覆盖写保证 （{@code set} 原子替换）。
 *
 * <p>
 * <b>编解码必须同源（StringCodec）</b>：{@code save} 与 {@link #find} / {@link #compareAndStatus} 统一经
 * {@link org.redisson.client.codec.StringCodec} 读写 JSON 字符串（对齐 {@code RedissonIdempotencyStore} 的 bucket 构造）。 缺省
 * codec（Kryo/JsonJackson 按实现版本而异）与 StringCodec 混用时，真实 Redis 下同一键两套字节形态互不可读——写入后轮询反序列化失败 → 500；本缺陷要求读写共用
 * {@link #bucket(String)} 单点构造，禁止任何一侧缺省 codec。
 */
@Slf4j
public class RedissonOperationStore implements OperationStore {

    private static final String KEY_PREFIX = "airedge:restful:lro:operation:";

    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedissonOperationStore(RedissonClient redisson, ObjectMapper objectMapper, Duration ttl) {
        this.redisson = redisson;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public void save(Operation operation) {
        bucket(operation.operationId()).set(serialize(operation), ttl);
    }

    @Override
    public Optional<Operation> find(String operationId) {
        String raw = bucket(operationId).get();
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(operationId, raw));
        } catch (IllegalStateException e) {
            // 遗留/损坏载荷（滚动升级期旧 codec 字节、schema 漂移）：单键不可读不应让轮询 500——记日志并按缺失处理
            log.warn("Operation 反序列化失败，按缺失处理: operationId={}", operationId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Operation> list(String owner) {
        List<Operation> result = new ArrayList<>();
        for (String key : redisson.getKeys().getKeysByPattern(KEY_PREFIX + "*")) {
            if (!key.startsWith(KEY_PREFIX)) {
                continue;
            }
            String operationId = key.substring(KEY_PREFIX.length());
            String raw = bucket(operationId).get();
            if (raw == null) {
                continue;
            }
            Operation operation;
            try {
                operation = deserialize(operationId, raw);
            } catch (IllegalStateException e) {
                // 单键损坏不应让整个列表 500：记日志并跳过该键
                log.warn("Operation 反序列化失败，列表跳过: operationId={}", operationId, e);
                continue;
            }
            if (owner == null || owner.isBlank() || owner.equals(operation.owner())) {
                result.add(operation);
            }
        }
        return result;
    }

    @Override
    public boolean saveIfAbsent(Operation operation) {
        // SET NX PX：原子 create-if-absent 且挂 TTL（触发端点 owner 兜底落库不与并发 Worker 竞争覆写）
        return bucket(operation.operationId()).setIfAbsent(serialize(operation), ttl);
    }

    @Override
    public Optional<Operation> compareAndStatus(String operationId, OperationStatus expected,
            java.util.function.UnaryOperator<Operation> updater) {
        RBucket<String> bucket = bucket(operationId);
        while (true) {
            String raw = bucket.get();
            if (raw == null) {
                return Optional.empty();
            }
            Operation current;
            try {
                current = deserialize(operationId, raw);
            } catch (IllegalStateException e) {
                // 损坏载荷与 find/list 同口径：单键不可读不应让 :cancel / 切面 500——记日志并按缺失处理
                log.warn("Operation 反序列化失败，CAS 按缺失处理: operationId={}", operationId, e);
                return Optional.empty();
            }
            if (OperationStatus.of(current) != expected) {
                return Optional.empty(); // 状态已被并发推进（先 cancel 后 complete 等）→ 放弃本侧写入
            }
            Operation updated = updater.apply(current);
            // CAS 失败 = 并发写竞争 → 重读重试（预期状态未变才能成功，消灭 last-write-wins）
            if (bucket.compareAndSet(raw, serialize(updated))) {
                // Redisson compareAndSet 的 Lua 为裸 SET（无 KEEPTTL/PX）会清除 TTL——成功后补挂，避免 CAS 终态键永不过期。
                // 已知窗口：CAS 与补挂非原子，进程在两步之间崩溃则该键无 TTL（终态键量小且可运维清理，Redisson 无 KEEPTTL 原语无法根治）
                bucket.expire(ttl);
                return Optional.of(updated);
            }
        }
    }

    @Override
    public void delete(String operationId) {
        bucket(operationId).delete();
    }

    /** 键空间唯一构造点：StringCodec 读写 JSON 字符串——save / find / compareAndStatus / delete 共用，杜绝编解码漂移。 */
    private RBucket<String> bucket(String operationId) {
        return redisson.getBucket(KEY_PREFIX + operationId, StringCodec.INSTANCE);
    }

    private String serialize(Operation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Operation 序列化失败: " + operation.operationId(), e);
        }
    }

    private Operation deserialize(String operationId, String raw) {
        try {
            Operation operation = objectMapper.readValue(raw, Operation.class);
            if (operation == null) {
                // JSON 字面量 "null" 合法（readValue 返回 null 引用、不抛异常）——并入损坏载荷口径，否则 find 的 Optional.of(null) / list 的 null 元素
                // NPE→500
                throw new IllegalStateException("Operation 载荷为 JSON null: " + operationId);
            }
            return operation;
        } catch (IOException e) {
            throw new IllegalStateException("Operation 反序列化失败: " + operationId, e);
        } catch (IllegalArgumentException e) {
            // record 紧凑构造器契约校验（operationId 非规范形态等）以 IAE 逃逸 readValue——并入损坏载荷口径，否则绕过 find/list/CAS 的
            // IllegalStateException 兜底 → 500
            throw new IllegalStateException("Operation 载荷契约校验失败: " + operationId, e);
        }
    }
}
