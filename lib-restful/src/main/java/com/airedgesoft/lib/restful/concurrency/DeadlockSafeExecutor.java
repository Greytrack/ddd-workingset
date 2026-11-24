package com.airedgesoft.lib.restful.concurrency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 同一聚合根类型批量写场景的<b>确定性加锁次序</b>执行器（Tier D「核心领域语义」基建：DeadlockSafeExecutor 主键升序加锁防死锁， v4-restful-patterns-compliance.md 第
 * 16 行）。
 *
 * <p>
 * <b>原理</b>：同一聚合根类型的批量实例（如批量移动任务）按不同顺序 SELECT ... FOR UPDATE / UPDATE 同一组行时形成锁等待环 → 死锁。
 * 全部事务按<b>主键升序</b>加锁即可消除环（全局全序）。本工具把「排序 → 依次执行」收口，调用方回调内执行加锁读 + 写； 结果按<b>原始入参顺序</b>回填（与 {@code BatchAlignment} 同款保序语义）。
 *
 * <p>
 * <b>职责边界</b>：本类不管理事务（事务边界由应用层 {@code *CommandService} 的 {@code @Transactional} 持有）；回调内异常即中断（已执行部分随事务回滚）。
 */
public final class DeadlockSafeExecutor {

    private DeadlockSafeExecutor() {
    }

    /**
     * 按键升序依次执行回调（无返回值形态）。
     *
     * @param keys
     *            聚合键（去重后按自然序排序执行）
     * @param lockAndAct
     *            加锁 + 业务动作回调
     */
    public static <K extends Comparable<K>> void run(List<K> keys, Consumer<K> lockAndAct) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        keys.stream().distinct().sorted(Comparator.naturalOrder()).forEach(lockAndAct);
    }

    /**
     * 按键升序依次执行回调并收集结果：结果按<b>原始入参顺序</b>返回（重复键只保留首个）。
     *
     * @param keys
     *            聚合键（原始顺序）
     * @param lockAndAct
     *            加锁 + 业务动作回调（返回该键的处理结果）
     * @return 结果列表（与去重后的 keys 顺序一致）
     */
    public static <K extends Comparable<K>, R> List<R> apply(List<K> keys, Function<K, R> lockAndAct) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Map<K, R> results = new LinkedHashMap<>();
        List<K> ordered = keys.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        for (K key : ordered) {
            results.put(key, lockAndAct.apply(key));
        }
        List<R> aligned = new ArrayList<>(results.size());
        for (K key : keys) {
            if (results.containsKey(key)) {
                aligned.add(results.remove(key));
            }
        }
        return List.copyOf(aligned);
    }
}
