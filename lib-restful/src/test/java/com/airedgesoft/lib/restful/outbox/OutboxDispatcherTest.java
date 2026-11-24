package com.airedgesoft.lib.restful.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 派发器单测（组件 10 发布侧；内存 Store 存根——失败隔离与标记语义）。
 */
class OutboxDispatcherTest {

    @Test
    void pollOnce_publishesInOrderAndMarksPublished() {
        MemoryStore store = new MemoryStore();
        store.append(OutboxRecord.pending("1", "demo.task.created", "demoTasks", "101", "{\"k\":1}"));
        store.append(OutboxRecord.pending("2", "demo.task.updated", "demoTasks", "101", "{\"k\":2}"));
        List<String> published = new ArrayList<>();
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, record -> published.add(record.id()), 50,
                Duration.ofSeconds(1));
        int count = dispatcher.pollOnce();
        assertThat(count).isEqualTo(2);
        assertThat(published).containsExactly("1", "2"); // 派发顺序 = 入箱顺序
        assertThat(store.fetchUnpublished(10)).isEmpty(); // 全部标记已发布
    }

    @Test
    void pollOnce_publisherFailure_isolatedAndRetriedNextRound() {
        MemoryStore store = new MemoryStore();
        store.append(OutboxRecord.pending("1", "broken", "demoTasks", "101", "{}"));
        store.append(OutboxRecord.pending("2", "healthy", "demoTasks", "102", "{}"));
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, record -> {
            if ("broken".equals(record.eventType())) {
                throw new IllegalStateException("MQ 不可用");
            }
        }, 50, Duration.ofSeconds(1));
        int count = dispatcher.pollOnce();
        assertThat(count).isEqualTo(1); // 失败隔离：healthy 照常发布
        List<OutboxRecord> remaining = store.fetchUnpublished(10);
        assertThat(remaining).extracting(OutboxRecord::id).containsExactly("1"); // 失败记录留箱重试
    }

    @Test
    void pollOnce_emptyBox_zeroDispatch() {
        OutboxDispatcher dispatcher = new OutboxDispatcher(new MemoryStore(), record -> {
        }, 50, Duration.ofSeconds(1));
        assertThat(dispatcher.pollOnce()).isZero();
    }

    /** 一整批失败记录留待重试，同时后续记录仍能送达。 */
    @Test
    void fullFailedBatchDoesNotStarveLaterRecords() {
        MemoryStore store = new MemoryStore();
        store.append(new OutboxRecord("1", "paused", "workflow", "101", "{}", 10, null));
        store.append(new OutboxRecord("2", "paused", "workflow", "102", "{}", 10, null));
        store.append(new OutboxRecord("3", "ready", "workflow", "103", "{}", 10, null));
        List<String> attempted = new ArrayList<>();
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, record -> {
            attempted.add(record.id());
            if ("paused".equals(record.eventType())) {
                throw new IllegalStateException("实例挂起");
            }
        }, 2, Duration.ofSeconds(1));

        assertThat(dispatcher.pollOnce()).isZero();
        assertThat(dispatcher.pollOnce()).isEqualTo(1);
        assertThat(dispatcher.pollOnce()).isZero();
        assertThat(attempted).containsExactly("1", "2", "3", "1", "2");
    }

    /** 持续填满后续批次时，恢复后的旧记录仍会在下一轮扫描时派发。 */
    @Test
    void retriesOldFailureWhileNewRecordsKeepTheQueueFull() {
        MemoryStore store = new MemoryStore();
        for (int id = 1; id <= 4; id++) {
            store.append(new OutboxRecord(Integer.toString(id), "ready", "workflow", "101", "{}", id, null));
        }
        List<String> attempted = new ArrayList<>();
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, record -> {
            if (attempted.isEmpty()) {
                attempted.add(record.id());
                throw new IllegalStateException("首次发布失败");
            }
            attempted.add(record.id());
        }, 2, Duration.ofSeconds(1));

        assertThat(dispatcher.pollOnce()).isEqualTo(1);
        for (int id = 5; id <= 8; id++) {
            store.append(new OutboxRecord(Integer.toString(id), "ready", "workflow", "101", "{}", id, null));
        }
        assertThat(dispatcher.pollOnce()).isEqualTo(2);
        assertThat(dispatcher.pollOnce()).isEqualTo(2);
        assertThat(attempted).containsExactly("1", "2", "3", "4", "1", "5");
    }

    @Test
    void pollOnceSafely_fetchFails_swallowedSoSchedulerKeepsPolling() {
        // fetchUnpublished 抛 RuntimeException（DB 抖动）：若不被吞掉，scheduleWithFixedDelay 会静默终止后续全部轮询
        java.util.concurrent.atomic.AtomicBoolean recovered = new java.util.concurrent.atomic.AtomicBoolean();
        OutboxRecord pending = OutboxRecord.pending("9", "demo.task.created", "demoTasks", "901", "{}");
        OutboxStore flakyStore = new OutboxStore() {
            @Override
            public void append(OutboxRecord record) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<OutboxRecord> fetchUnpublished(int limit, OutboxRecord after, OutboxRecord through) {
                if (!recovered.compareAndSet(false, true)) {
                    return List.of(pending);
                }
                throw new IllegalStateException("连接池瞬时耗尽");
            }

            /** 此场景仅模拟批次查询因连接池短暂故障而失败。 */
            @Override
            public Optional<OutboxRecord> findLastUnpublished() {
                return Optional.of(pending);
            }

            @Override
            public void markPublished(String id) {
                // 派发成功的标记（本用例由 pollOnce 内部调用）
            }
        };
        List<String> published = new ArrayList<>();
        OutboxDispatcher dispatcher = new OutboxDispatcher(flakyStore, record -> published.add(record.id()), 50,
                Duration.ofSeconds(1));

        org.assertj.core.api.Assertions.assertThatCode(dispatcher::pollOnceSafely).doesNotThrowAnyException();

        dispatcher.pollOnceSafely(); // 下轮恢复后照常派发（轮询未被终止）
        assertThat(published).containsExactly("9");
    }

    /** 内存存根（接口语义：fetch 升序、markPublished 幂等）。 */
    private static final class MemoryStore implements OutboxStore {

        private final List<OutboxRecord> records = new ArrayList<>();

        @Override
        public void append(OutboxRecord record) {
            records.add(record);
        }

        @Override
        public List<OutboxRecord> fetchUnpublished(int limit, OutboxRecord after, OutboxRecord through) {
            var order = java.util.Comparator.comparingLong(OutboxRecord::createTimeMs).thenComparing(OutboxRecord::id);
            return records.stream().filter(record -> !record.published())
                    .filter(record -> after == null || order.compare(record, after) > 0)
                .filter(record -> through == null || order.compare(record, through) <= 0)
                    .sorted(order).limit(limit).toList();
        }

        /** 定位当前未发布队尾。 */
        @Override
        public Optional<OutboxRecord> findLastUnpublished() {
            var order = java.util.Comparator.comparingLong(OutboxRecord::createTimeMs).thenComparing(OutboxRecord::id);
            return records.stream().filter(record -> !record.published()).max(order);
        }

        @Override
        public void markPublished(String id) {
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).id().equals(id)) {
                    records.set(i, records.get(i).asPublished());
                }
            }
        }
    }
}
