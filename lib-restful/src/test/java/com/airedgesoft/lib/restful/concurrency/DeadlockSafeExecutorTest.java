package com.airedgesoft.lib.restful.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeadlockSafeExecutor 单测（组件 9；主键升序执行次序与结果保序回填）。
 */
class DeadlockSafeExecutorTest {

    @Test
    void run_executesInKeyAscendingOrder_despiteInputOrder() {
        List<Long> executionOrder = new CopyOnWriteArrayList<>();
        DeadlockSafeExecutor.run(List.of(30L, 10L, 20L), executionOrder::add);
        assertThat(executionOrder).containsExactly(10L, 20L, 30L); // 全局加锁全序，锁等待环消除
    }

    @Test
    void run_deduplicatesKeys() {
        List<Long> executionOrder = new CopyOnWriteArrayList<>();
        DeadlockSafeExecutor.run(List.of(2L, 2L, 1L), executionOrder::add);
        assertThat(executionOrder).containsExactly(1L, 2L);
    }

    @Test
    void apply_resultsAlignedToInputOrder_notExecutionOrder() {
        List<Long> executionOrder = new ArrayList<>();
        List<String> results = DeadlockSafeExecutor.apply(List.of(30L, 10L, 20L), key -> {
            executionOrder.add(key);
            return "r" + key;
        });
        assertThat(executionOrder).containsExactly(10L, 20L, 30L); // 升序执行
        assertThat(results).containsExactly("r30", "r10", "r20"); // 结果按原始入参顺序回填
    }

    @Test
    void emptyAndNull_inputsDegenerate() {
        DeadlockSafeExecutor.<Long>run(List.of(), k -> {
            throw new AssertionError("不应执行");
        });
        assertThat(DeadlockSafeExecutor.<Long, Long>apply(null, k -> k)).isEmpty();
    }
}
