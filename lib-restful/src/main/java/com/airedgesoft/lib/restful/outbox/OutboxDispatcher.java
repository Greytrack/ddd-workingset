package com.airedgesoft.lib.restful.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbox 轮询派发器（Transactional Outbox 的发布侧；至少一次投递）。
 *
 * <p>
 * {@link #pollOnce()} 逐条发布当前批次。失败记录保留，扫描达到本轮开始时的队尾后从队首重试。
 * {@link #start()} / {@link #stop()} 提供单线程轮询循环（间隔可配）。宿主自管调度时直接调用 {@link #pollOnce()}。
 *
 * <p>
 * <b>消费端契约</b>：至少一次语义下按 {@code OutboxRecord#id} 幂等去重（重复投递不可避免）。
 */
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxStore store;
    private final java.util.function.Consumer<OutboxRecord> publisher;
    private final int batchSize;
    private final Duration pollInterval;
    private final Object pollMonitor = new Object();
    private ScheduledExecutorService scheduler;
    private OutboxRecord scanPosition;
    private OutboxRecord scanEnd;

    public OutboxDispatcher(OutboxStore store, java.util.function.Consumer<OutboxRecord> publisher, int batchSize,
            Duration pollInterval) {
        this.store = Objects.requireNonNull(store, "store 不能为空");
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
        this.batchSize = batchSize <= 0 ? 50 : batchSize;
        this.pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
                ? Duration.ofSeconds(2)
                : pollInterval;
    }

    /** 推进一个批次，达到固定队尾后释放游标，让下一次轮询重试队首失败项。 */
    public int pollOnce() {
        // 启停独立加锁，停止调度时无需等待发布回调释放游标锁。
        synchronized (pollMonitor) {
            if (scanPosition == null) {
                scanEnd = store.findLastUnpublished().orElse(null);
            }
            if (scanEnd == null) {
                return 0;
            }
            List<OutboxRecord> pending = store.fetchUnpublished(batchSize, scanPosition, scanEnd);
            scanPosition = null;
            if (pending.size() == batchSize) {
                OutboxRecord last = pending.get(pending.size() - 1);
                if (!last.id().equals(scanEnd.id())) {
                    scanPosition = last;
                }
            }
            int published = 0;
            for (OutboxRecord record : pending) {
                try {
                    publisher.accept(record);
                    store.markPublished(record.id());
                    published++;
                } catch (RuntimeException e) {
                    log.warn("Outbox 记录发布失败（留箱重试）: id={}, eventType={}", record.id(), record.eventType(), e);
                }
            }
            return published;
        }
    }

    /**
     * 调度安全包装：{@code scheduleWithFixedDelay} 的任务抛出未捕获异常时后续执行被静默抑制—— 单轮整体失败（如 {@code fetchUnpublished} 遇 DB
     * 抖动）只记日志不终止轮询，记录留箱下轮重试（至少一次投递承诺）。 包私有以便单测直接验证吞异常语义。
     */
    void pollOnceSafely() {
        try {
            pollOnce();
        } catch (Throwable t) {
            log.error("Outbox 单轮派发失败（轮询继续，记录留箱重试）", t);
        }
    }

    /** 启动后台轮询（单线程；重复调用幂等）。 */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "outbox-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::pollOnceSafely, pollInterval.toMillis(), pollInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("Outbox 派发器已启动: batchSize={}, interval={}", batchSize, pollInterval);
    }

    /** 停止后台轮询（幂等；未启动为 no-op）。 */
    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        scheduler = null;
    }

    /** 后台轮询是否在运行（{@link #start()} 后 true，{@link #stop()} 后 false）。 */
    public synchronized boolean isRunning() {
        return scheduler != null;
    }
}
