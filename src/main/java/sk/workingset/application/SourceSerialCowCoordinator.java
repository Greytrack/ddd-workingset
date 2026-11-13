package sk.workingset.application;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.workingset.domain.LocalId;

/** 按来源串行执行 COW：同一来源的事件按顺序处理，避免旧事件覆盖新快照。 */
public final class SourceSerialCowCoordinator {
    private static final Logger log = LoggerFactory.getLogger(SourceSerialCowCoordinator.class);
    private static final int RECENT_SOURCE_LIMIT = 1024;

    private final Map<LocalId, PriorityQueue<Task>> queues = new HashMap<>();
    private final LinkedHashMap<LocalId, Long> lastExecuted = new LinkedHashMap<>(16, 0.75f, true);
    private final long gapWaitNanos;

    public SourceSerialCowCoordinator() {
        this(Duration.ofSeconds(1));
    }

    public SourceSerialCowCoordinator(Duration gapWait) {
        if (gapWait.isNegative()) {
            throw new IllegalArgumentException("Order gap wait must not be negative");
        }
        this.gapWaitNanos = gapWait.toNanos();
    }

    public void runSerial(LocalId sourceLocalId, long sequence, Runnable task) {
        var completion = new CompletableFuture<Void>();
        boolean drain;
        synchronized (queues) {
            drain = !queues.containsKey(sourceLocalId);
            queues.computeIfAbsent(sourceLocalId,
                    ignored -> new PriorityQueue<>(Comparator.comparingLong(Task::sequence)))
                    .add(new Task(sequence, task, completion));
            queues.notifyAll();
        }
        if (drain) {
            drain(sourceLocalId);
        }
        try {
            completion.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (failure.getCause() instanceof Error cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private void drain(LocalId source) {
        while (true) {
            Task task;
            long previous;
            synchronized (queues) {
                var queue = queues.get(source);
                task = queue.peek();
                if (task == null) {
                    queues.remove(source);
                    return;
                }
                previous = lastExecuted.getOrDefault(source, 0L);
                long started = System.nanoTime();
                while (isGap(task.sequence(), previous)) {
                    long remaining = gapWaitNanos - (System.nanoTime() - started);
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(queues, remaining);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    task = queue.peek();
                }
                queue.remove();
            }
            if (isGap(task.sequence(), previous)) {
                log.warn("ORDER_GAP sourceLocalId={} expected={} actual={}",
                        source.value(), previous + 1, task.sequence());
            }
            try {
                task.work().run();
                task.completion().complete(null);
            } catch (RuntimeException | Error failure) {
                task.completion().completeExceptionally(failure);
            } finally {
                synchronized (queues) {
                    lastExecuted.put(source, Math.max(previous, task.sequence()));
                    if (lastExecuted.size() > RECENT_SOURCE_LIMIT) {
                        lastExecuted.pollFirstEntry();
                    }
                }
            }
        }
    }

    private static boolean isGap(long sequence, long previous) {
        return sequence > previous && sequence - previous > 1;
    }

    private record Task(long sequence, Runnable work, CompletableFuture<Void> completion) {}
}
