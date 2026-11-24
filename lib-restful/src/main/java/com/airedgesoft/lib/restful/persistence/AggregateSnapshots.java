package com.airedgesoft.lib.restful.persistence;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务级<b>加载快照登记</b>（持久化侧）：记录本事务内加载过的聚合及其存储版本，供保存时取 CAS 基线。
 *
 * <p>
 * <b>与 HTTP 协议无关</b>：本类只回答「这个聚合加载时的存储版本是多少」，不涉及任何 HTTP 概念——业务仓储如需基线，向本类取值即可， 无需感知 ETag / lockedVersion 之类的协议细节。
 *
 * <p>
 * <b>基线语义</b>：同一事务内同一身份的多次加载取<b>首次</b>快照（不以后加载者覆盖）——陈旧实例因此以旧基线做 CAS，冲突被数据库拒绝 （乐观锁失败），不会静默覆盖他人提交。实例级查询
 * {@link #baselineOf(Object)} 优先，未登记时回退到身份的首次快照 {@link #firstSnapshotOf(Object)}。
 *
 * <p>
 * <b>作用域</b>：事务级（写路径必有事务）；事务结束自动清理。事务外（如只读装配）不登记——基线只在写入时需要。
 */
public final class AggregateSnapshots {

    private static final Object RESOURCE_KEY = new Object();

    private AggregateSnapshots() {
    }

    /**
     * 登记一次加载（聚合实例 → 存储版本；身份 → 首次快照）。
     *
     * @param identity
     *            聚合身份
     * @param aggregate
     *            加载得到的聚合实例
     * @param version
     *            存储版本（null = 该表无版本列 / 旧库 NULL 版本）
     */
    public static void recordLoaded(Object identity, Object aggregate, Long version) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        State state = state();
        if (aggregate != null) {
            state.byInstance.putIfAbsent(aggregate, version);
        }
        if (identity != null) {
            state.firstSnapshotByIdentity.putIfAbsent(String.valueOf(identity), version);
        }
    }

    /**
     * 该聚合实例加载时的存储版本（CAS 基线）；未登记 → 身份的首次快照 → null（视为新聚合）。
     */
    public static Long baselineOf(Object aggregate) {
        if (aggregate == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        State state = state();
        Long version = state.byInstance.get(aggregate);
        return version != null ? version : state.firstSnapshotByIdentity.get(String.valueOf(aggregate));
    }

    /**
     * 该身份的当前存储版本（保存后推进的值；未登记 → null）。
     */
    public static Long currentVersionOf(Object identity) {
        if (identity == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        return state().firstSnapshotByIdentity.get(String.valueOf(identity));
    }

    /**
     * 保存后推进基线：存库成功后的新版本成为下一次 CAS 基线（同事务内连续保存时，第二次以最新版本做 CAS）。
     *
     * @param identity
     *            聚合身份
     * @param aggregate
     *            被保存的聚合实例（推进<b>该实例</b>的基线——并发基线按实例隔离）
     * @param version
     *            存库后的新版本
     */
    public static void recordSaved(Object identity, Object aggregate, Long version) {
        if (version == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        State state = state();
        if (aggregate != null) {
            state.byInstance.put(aggregate, version);
        }
        if (identity != null) {
            state.firstSnapshotByIdentity.put(String.valueOf(identity), version);
        }
    }

    /**
     * 该身份在本事务内首次加载的存储版本（未加载 → null）。
     */
    public static Long firstSnapshotOf(Object identity) {
        if (identity == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        return state().firstSnapshotByIdentity.get(String.valueOf(identity));
    }

    /** 当前事务的快照状态（事务结束随资源释放）。 */
    private static final class State {

        /** 聚合实例 → 加载时的存储版本。 */
        final Map<Object, Long> byInstance = new IdentityHashMap<>();
        /** 身份 → 首次加载的存储版本。 */
        final Map<String, Long> firstSnapshotByIdentity = new HashMap<>();
    }

    private static State state() {
        State state = (State) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (state == null) {
            state = new State();
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, state);
            State registered = state;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == registered) {
                        TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                    }
                }
            });
        }
        return state;
    }
}
