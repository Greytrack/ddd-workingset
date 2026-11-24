package com.airedgesoft.lib.restful.lro;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RedissonOperationStore} 契约单测（Mock RedissonClient + 内存 Map 模拟 RBucket；不起真实 Redis）。
 *
 * <p>
 * 缺陷回归锚：{@code save} 与 {@code find} 必须同一 codec（StringCodec）——缺省 codec 写入 / StringCodec 读取在真实 Redis 下互不可读，轮询 500；
 * 本测试以「写入值即 String JSON 字节」+「bucket 构造传入 StringCodec」双断言钉死编解码同源。附带覆盖 Redis 侧 CAS 取消语义（compareAndSet 竞争重试）。
 */
class RedissonOperationStoreTest {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_101 = "airedge:restful:lro:operation:101";

    private final Map<String, String> redis = new ConcurrentHashMap<>();
    /** 模拟 Redis 键 TTL：save/setIfAbsent 挂 TTL；compareAndSet 的 Lua 裸 SET 清除 TTL；expire 补挂（N1 回归锚）。 */
    private final Map<String, Duration> ttls = new ConcurrentHashMap<>();
    private final AtomicReference<Object> requestedCodec = new AtomicReference<>();
    /** 模拟 CAS 竞争：出队非空时先以并发方身份改写 redis，再执行本次 compareAndSet（首次必然 miss → 强制重读）。 */
    private final ConcurrentLinkedQueue<UnaryOperator<String>> casRacers = new ConcurrentLinkedQueue<>();
    private RedissonOperationStore store;

    @BeforeEach
    void setUp() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getBucket(anyString(), any())).thenAnswer(inv -> {
            requestedCodec.set(inv.getArgument(1));
            return fakeBucket(inv.getArgument(0));
        });
        RKeys keys = mock(RKeys.class);
        when(client.getKeys()).thenReturn(keys);
        when(keys.getKeysByPattern(anyString())).thenAnswer(inv -> redis.keySet());
        store = new RedissonOperationStore(client, new ObjectMapper(), TTL);
    }

    private RBucket<String> fakeBucket(String name) {
        RBucket<String> bucket = mock(RBucket.class);
        when(bucket.get()).thenAnswer(inv -> redis.get(name));
        org.mockito.Mockito.doAnswer(inv -> {
            redis.put(name, inv.getArgument(0));
            ttls.put(name, inv.getArgument(1));
            return null;
        }).when(bucket).set(anyString(), any(Duration.class));
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenAnswer(inv -> {
            if (redis.containsKey(name)) {
                return false;
            }
            redis.put(name, inv.getArgument(0));
            ttls.put(name, inv.getArgument(1));
            return true;
        });
        when(bucket.expire(any(Duration.class))).thenAnswer(inv -> {
            ttls.put(name, inv.getArgument(0));
            return true;
        });
        when(bucket.delete()).thenAnswer(inv -> {
            ttls.remove(name);
            return redis.remove(name) != null;
        });
        when(bucket.compareAndSet(anyString(), anyString())).thenAnswer(inv -> {
            String expect = inv.getArgument(0);
            String update = inv.getArgument(1);
            UnaryOperator<String> racer = casRacers.poll();
            if (racer != null) {
                racer.apply(name); // 并发方抢先改写 → expect 失配
            }
            if (expect.equals(redis.get(name))) {
                redis.put(name, update);
                ttls.remove(name); // Redisson compareAndSet 的 Lua 为裸 SET（无 KEEPTTL/PX）→ 清除 TTL
                return true;
            }
            return false;
        });
        return bucket;
    }

    // ── 编解码同源（StringCodec） ──

    @DisplayName("编解码同源：save 侧 bucket 传 StringCodec 且写入值即纯 JSON 字符串")
    @Test
    void save_usesStringCodecAndWritesPlainJsonString() {
        store.save(Operation.pending("101", "copy"));
        assertThat(requestedCodec.get()).isSameAs(StringCodec.INSTANCE); // save 侧 codec = StringCodec
        String raw = redis.get("airedge:restful:lro:operation:101");
        assertThat(raw).startsWith("{").contains("\"operationId\":\"101\""); // 写入即 String JSON（非二次编码字节）
    }

    @DisplayName("编解码同源：find 侧与 save 同用 StringCodec，Operation 往返一致且缺失返回空")
    @Test
    void find_usesSameCodecAndRoundTrips() {
        store.save(Operation.pending("101", "copy").withProgress(40));
        assertThat(requestedCodec.get()).isSameAs(StringCodec.INSTANCE); // find 侧 codec = StringCodec（与 save 同源）
        Optional<Operation> found = store.find("101");
        assertThat(found).isPresent();
        assertThat(found.get().progress()).isEqualTo(40);
        assertThat(found.get().taskType()).isEqualTo("copy");
        assertThat(store.find("missing")).isEmpty();
    }

    // ── Redis 侧 CAS 终态仲裁 ──

    @DisplayName("Redis CAS 取消：期望 RUNNING 匹配时原子应用取消终态并写入 Redis")
    @Test
    void compareAndStatus_running_appliesAtomically() {
        store.save(Operation.pending("101", "copy"));
        Optional<Operation> updated = store.compareAndStatus("101", OperationStatus.RUNNING,
                op -> op.withError(Operation.cancelled()));
        assertThat(updated).isPresent();
        assertThat(updated.get().error().reason()).isEqualTo("CANCELLED");
        assertThat(redis.get("airedge:restful:lro:operation:101")).contains("\"reason\":\"CANCELLED\"");
    }

    @DisplayName("Redis CAS 取消：状态不匹配时返回空且不覆写既有完成终态")
    @Test
    void compareAndStatus_statusMismatch_returnsEmptyWithoutWrite() {
        String completedJson = "{\"name\":\"operations/101\",\"metadata\":{\"operationId\":\"101\",\"taskType\":\"copy\","
                + "\"progress\":100,\"createTime\":\"2026-03-15T09:30:00Z\",\"updateTime\":\"2026-03-15T09:30:02Z\"},"
                + "\"done\":true,\"response\":\"ok\"}";
        redis.put("airedge:restful:lro:operation:101", completedJson);
        assertThat(store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withError(Operation.cancelled())))
                .isEmpty(); // 已完成 → 取消侧落空
        assertThat(redis.get("airedge:restful:lro:operation:101")).isEqualTo(completedJson); // 不覆写
    }

    @DisplayName("Redis CAS 取消：并发竞争致 compareAndSet 失配后基于最新重读重试成功，不丢并发推进")
    @Test
    void compareAndStatus_casContention_retriesWithFreshRead() {
        store.save(Operation.pending("101", "copy"));
        // 并发方（Worker 进度推进）在取消侧 CAS 前抢先写入 → 首次 compareAndSet 必然 miss
        casRacers.add(name -> redis.put(name,
                "{\"name\":\"operations/101\",\"metadata\":{\"operationId\":\"101\","
                        + "\"taskType\":\"copy\",\"progress\":40,\"createTime\":\"2026-03-15T09:30:00Z\","
                        + "\"updateTime\":\"2026-03-15T09:30:02Z\"},\"done\":false}"));
        Optional<Operation> updated = store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withProgress(50));
        assertThat(updated).isPresent(); // 基于最新状态重读后成功（不丢失并发推进）
        assertThat(updated.get().progress()).isEqualTo(50);
        assertThat(redis.get("airedge:restful:lro:operation:101")).contains("\"progress\":50");
    }

    @DisplayName("Redis CAS 取消：operation 不存在返回空")
    @Test
    void compareAndStatus_missingOperation_returnsEmpty() {
        assertThat(store.compareAndStatus("missing", OperationStatus.RUNNING, op -> op.withProgress(1))).isEmpty();
    }

    // ── 删除（AIP-151 deleteOperation 的 Store 侧：仅删不给，状态裁决在端点） ──

    @DisplayName("删除：已存在 operation 从 Redis 移除键且删除后不可再读")
    @Test
    void delete_existingOperation_removesKeyFromRedis() {
        store.save(Operation.pending("101", "copy"));
        store.delete("101");
        assertThat(redis.get("airedge:restful:lro:operation:101")).isNull();
        assertThat(store.find("101")).isEmpty(); // 已删不可读
    }

    @DisplayName("删除：operation 不存在时静默无操作，不抛异常")
    @Test
    void delete_missingOperation_isSilentNoop() {
        store.delete("missing"); // 不存在亦静默，不抛异常
        assertThat(redis).isEmpty();
    }

    // ── TTL 保留（N1：compareAndSet 裸 SET 清 TTL → expire 补挂） ──

    @DisplayName("TTL 保留：CAS 成功后补挂 TTL，终态键不致永不过期（N1 回归锚）")
    @Test
    void compareAndStatus_success_reappliesTtlClearedByBareSet() {
        store.save(Operation.pending("101", "copy"));
        assertThat(ttls.get(KEY_101)).isEqualTo(TTL); // save 挂 TTL
        Optional<Operation> updated = store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withProgress(50));
        assertThat(updated).isPresent();
        // mock 已模拟 compareAndSet 裸 SET 清 TTL；修复后 expire 补挂 → TTL 仍在（否则键永不过期、无界增长）
        assertThat(ttls.get(KEY_101)).isEqualTo(TTL);
    }

    @DisplayName("TTL 保留：状态不匹配落空时不触发 expire（不写就不动 TTL）")
    @Test
    void compareAndStatus_statusMismatch_leavesTtlUntouched() {
        store.save(Operation.pending("101", "copy").withResult(java.util.Map.of("ok", true))); // done=true 终态
        ttls.remove(KEY_101); // 人为置无 TTL，验证落空分支不会补挂
        assertThat(store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withProgress(50))).isEmpty(); // 终态 ≠
                                                                                                                 // RUNNING
                                                                                                                 // → 落空
        assertThat(ttls.get(KEY_101)).isNull(); // 落空分支不补挂 TTL
    }

    // ── 损坏载荷容忍（#9 find/list + N5 CAS） ──

    @DisplayName("损坏载荷：find 按缺失返回空，不抛 500（#9）")
    @Test
    void find_corruptPayload_returnsEmpty() {
        redis.put("airedge:restful:lro:operation:bad", "{not valid json");
        assertThat(store.find("bad")).isEmpty();
    }

    @DisplayName("损坏载荷：list 跳过坏键、保留好键，不抛 500（#9）")
    @Test
    void list_corruptPayload_skipsBadKey() {
        redis.put("airedge:restful:lro:operation:bad", "{not valid json");
        store.save(Operation.pending("101", "copy"));
        assertThat(store.list(null)).extracting(Operation::operationId).containsExactly("101");
    }

    @DisplayName("损坏载荷：CAS 循环内坏键按缺失返回空，不抛 500（N5）")
    @Test
    void compareAndStatus_corruptPayload_returnsEmpty() {
        redis.put(KEY_101, "{not valid json");
        assertThat(store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withProgress(1))).isEmpty();
    }

    // ── JSON null 载荷容忍（pass-3：合法 JSON "null" → readValue 返回 null 引用，须并入损坏口径而非 NPE→500） ──

    @DisplayName("JSON null 载荷：find 按缺失返回空，不抛 NPE/500")
    @Test
    void find_nullPayload_returnsEmpty() {
        redis.put("airedge:restful:lro:operation:bad", "null");
        assertThat(store.find("bad")).isEmpty();
    }

    @DisplayName("JSON null 载荷：list 跳过 null 键、保留好键，不抛 NPE/500")
    @Test
    void list_nullPayload_skipsBadKey() {
        redis.put("airedge:restful:lro:operation:bad", "null");
        store.save(Operation.pending("101", "copy"));
        assertThat(store.list(null)).extracting(Operation::operationId).containsExactly("101");
    }

    @DisplayName("JSON null 载荷：CAS 循环内 null 键按缺失返回空，不抛 NPE/500")
    @Test
    void compareAndStatus_nullPayload_returnsEmpty() {
        redis.put(KEY_101, "null");
        assertThat(store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withProgress(1))).isEmpty();
    }

    // ── 原子 create-if-absent（N4：触发端点 owner 兜底落库不覆写并发 Worker） ──

    @DisplayName("saveIfAbsent：不存在则原子创建并挂 TTL；已存在不覆写返回 false（N4）")
    @Test
    void saveIfAbsent_createsWhenAbsent_doesNotOverwrite() {
        assertThat(store.saveIfAbsent(Operation.pending("101", "copy"))).isTrue();
        assertThat(ttls.get(KEY_101)).isEqualTo(TTL);
        assertThat(store.saveIfAbsent(Operation.pending("101", "copy").withProgress(99))).isFalse();
        assertThat(store.find("101")).isPresent();
        assertThat(store.find("101").get().progress()).isNotEqualTo(99); // 既有记录未被覆写
    }
}
