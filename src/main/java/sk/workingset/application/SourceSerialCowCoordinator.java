package sk.workingset.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.workingset.domain.LocalId;

public final class SourceSerialCowCoordinator {
    private static final Logger log = LoggerFactory.getLogger(SourceSerialCowCoordinator.class);
    private static final int RECENT_SOURCE_LIMIT = 1024;

    private final Map<LocalId, PriorityQueue<Task>> queues = new HashMap<>();
    private final LinkedHashMap<LocalId, Long> lastExecuted = new LinkedHashMap<>(16, 0.75f, true);

    public void runSerial(LocalId sourceLocalId, long sequence, Runnable task) {
        var completion = new CompletableFuture<Void>();
        boolean drain;
        synchronized (queues) {
            drain = !queues.containsKey(sourceLocalId);
            queues.computeIfAbsent(sourceLocalId,
                    ignored -> new PriorityQueue<>(Comparator.comparingLong(Task::sequence)))
                    .add(new Task(sequence, task, completion));
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
                task = queues.get(source).poll();
                if (task == null) {
                    queues.remove(source);
                    return;
                }
                previous = lastExecuted.getOrDefault(source, 0L);
            }
            if (task.sequence() > previous && task.sequence() - previous > 1) {
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

    private record Task(long sequence, Runnable work, CompletableFuture<Void> completion) {}
}
