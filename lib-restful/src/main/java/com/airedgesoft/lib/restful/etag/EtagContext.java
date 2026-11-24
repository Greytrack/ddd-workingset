package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.softdelete.SoftDeleteContext;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 请求级并发上下文：期望版本（adapters 绑定）与当前版本（infrastructure 观测 / 回写）在一条请求内的<b>隐式传输通道</b>—— 令 application / domain 对
 * {@code lockedVersion} 完全透明（规范 §6 / §6.7 / §7.2）。
 *
 * <p>
 * <b>三类状态，三把不同的钥匙</b>（混用即错配）：
 * <ul>
 * <li><b>期望版本</b>（客户端 body {@code lockedVersion} 携带）：按<b>资源相对名</b>（{@code collection/id}，AIP-122）键控——命名空间随资源集合，跨 BC /
 * 跨集合的同号资源不串；</li>
 * <li><b>当前版本</b>（响应 body {@code lockedVersion} 之源；ETag 恒为表示 hash，不由此派生）：同样按<b>资源相对名</b>键控；</li>
 * <li><b>加载版本</b>（CAS 基线）：按<b>聚合实例身份</b>键控——乐观锁的基线必须随<b>该实例的快照</b>，不能按资源 id 单槽 （同一请求内两次加载同一 id
 * 时，后加载者不得覆盖先加载者的基线，否则陈旧实例会以较新版本做 CAS → 静默覆盖并发提交）；</li>
 * <li><b>已消费期望</b>（原生 ID 集合）：保存即兑现期望——同请求写后重读（组装响应等）见到新版本属合法，不再以陈旧期望断言； 一次写请求对应一次期望兑现。</li>
 * </ul>
 *
 * <p>
 * <b>原生 ID 双槽</b>：仓储 / 实体回调多数只有裸 id（无集合段），故相对名与原生 ID 成对写入、读取只做<b>精确匹配</b>（相对名与裸 id 互查，无后缀模糊回退）—— 同请求内跨集合的同号 id
 * 理论上可互串，由标识规范（§2 雪花 ID 全局唯一）兜底；采集端具备集合段时应优先传相对名。
 *
 * <p>
 * <b>实现</b>：优先挂 {@link RequestContextHolder} 请求属性（生命周期由 Spring 管理，请求结束即清理）； 无 HTTP 上下文（单测直调 / 定时作业）回退线程级状态——此时须由调用方用
 * {@link #withExpected} 建立显式作用域（finally 还原，防线程池泄漏与陈旧代数）。本类只做<b>传输</b>不做策略：版本比对断言收口在加载登记点， 409 统一使用框架码
 * {@link DomainRestCode#VERSION_CONFLICT}。
 *
 * <p>
 * <b>边界约束</b>：默认仅适用同步 MVC 单线程链路；异步 / 并行流不自动传播——LRO Worker / 流式导出等跨线程场景用 {@link #capture()} / {@link #restore(Snapshot)}
 * 显式传递（CAS 基线不随快照传播，目标线程重新加载时重建）。
 */
public final class EtagContext {

    private static final String REQUEST_ATTRIBUTE = EtagContext.class.getName();

    /** 线程级回退状态（仅非 HTTP 场景；请求属性存在时不使用）。 */
    private static final ThreadLocal<State> FALLBACK = new ThreadLocal<>();

    private EtagContext() {
    }

    // ════════ 绑定（adapters / 切面） ════════

    /**
     * 绑定 body {@code lockedVersion}（对外代数）到指定资源：内部换算为存储版本 {@code generation - 1}。
     *
     * <p>
     * 版本载体是 body（决策 C5 / D13），本方法是切面唯一的绑定入口（单资源与批量逐条均经此）。
     *
     * @param resourceName
     *            资源相对名（{@code collection/id}）或原生 ID
     * @param generation
     *            对外并发代数（从 1 起算，必须 &gt;= 1）
     */
    public static void bindLockedVersion(String resourceName, long generation) {
        if (resourceName == null) {
            return;
        }
        State state = state();
        long storageVersion = generation - 1;
        state.expected.put(resourceName, storageVersion);
        String rawId = rawIdOf(resourceName);
        if (rawId != null && !rawId.equals(resourceName)) {
            state.expected.put(rawId, storageVersion);
        }
    }

    // ════════ 读取（infrastructure） ════════

    /** 单资源期望存储版本（未绑定 / 非本资源 → null = 不做并发断言）。支持 AIP-122 资源名或裸 ID 匹配。 */
    public static Long expectedStorageVersion(Object identityOrName) {
        if (identityOrName == null) {
            return null;
        }
        State state = stateOrNull();
        if (state == null) {
            return null;
        }
        // 键只做精确匹配（相对名与原生 ID 双槽由绑定侧成对写入）：禁止按 id 后缀模糊回退——
        // 同请求加载跨集合同号行时会命中错误槽（误 409 / lockedVersion 取错版本），且 HashMap 迭代序不确定。
        String key = String.valueOf(identityOrName);
        if (state.expected.containsKey(key)) {
            return state.expected.get(key);
        }
        String rawId = rawIdOf(key);
        if (state.expected.containsKey(rawId)) {
            return state.expected.get(rawId);
        }
        return null;
    }

    public static Long expectedStorageVersion(String resourceName) {
        return expectedStorageVersion((Object) resourceName);
    }

    /** 前置条件断言：期望存储版本与实际版本不符 → 409（detail 携带资源 id，批量场景可定位条目）。 */
    public static void assertExpectedVersion(Object identityOrName, Long actualVersion) {
        Long expected = expectedStorageVersion(identityOrName);
        if (expected == null) {
            return;
        }
        long actual = (actualVersion == null ? 0L : actualVersion);
        if (!expected.equals(actual)) {
            throw new DomainException(DomainRestCode.VERSION_CONFLICT, identityOrName, expected);
        }
    }

    // ════════ 观测与回写（infrastructure） ════════
    /**
     * 仓储加载行后记录：① 该<b>聚合实例</b>的加载版本（CAS 基线）；② 该资源的当前版本（响应 body {@code lockedVersion} 之源）；③ 存活行自动校验版本并发前置条件（409）。
     *
     * @param identityOrName
     *            资源相对名（{@code collection/id}）或原生 ID
     * @param instance
     *            聚合实例（加载得到的那一个对象）——CAS 基线按实例隔离
     * @param storageVersion
     *            存储版本（null = 该表无版本列 / 旧库 NULL）
     */
    public static void recordLoaded(Object identityOrName, Object instance, Long storageVersion) {
        State state = state();
        if (state.current.size() >= State.OBSERVE_LIMIT) {
            // 显式登记与自动观测同一上限（非 HTTP 长驻线程的内存兜底；超限多发生在失控作业）——
            // 静默 return 会让 CAS 基线无声丢失，同 recordObserved 补 once-warn 提示
            if (!state.observationOverflowReported) {
                state.observationOverflowReported = true;
                State.LOGGER.warn("ETag 显式登记超过单请求 {} 行上限，停止登记（CAS 基线将缺失，并发防护失效；非 HTTP 长驻线程请改用显式作用域）",
                        State.OBSERVE_LIMIT);
            }
            return;
        }
        state.loaded.put(instance, storageVersion);
        if (identityOrName != null) {
            String key = String.valueOf(identityOrName);
            state.current.put(key, storageVersion);
            String rawId = rawIdOf(key);
            if (rawId != null && !rawId.equals(key)) {
                state.current.put(rawId, storageVersion);
            }
            // 版本并发前置条件：仅在本请求尚未保存过该资源时断言（保存即消费期望——写后重读组装响应合法地见到新版本）
            if (!state.expectationConsumed.contains(rawIdOf(key)) && !isSoftDeleted(instance)) {
                assertExpectedVersion(identityOrName, storageVersion);
            }
        }
    }

    public static void recordLoaded(String resourceName, Object instance, Long storageVersion) {
        recordLoaded((Object) resourceName, instance, storageVersion);
    }

    /**
     * 框架自动观测登记（Spring Data JDBC 实体回调专用）：同 {@link #recordLoaded} 登记<b>加载版本</b>与<b>当前版本</b>，但<b>不做</b>期望版本前置断言——
     * 同一请求会加载多张表的行，跨表同值主键若在自动观测路径断言将误触发 409；加载期比对保留在业务聚合自身的登记点 （{@link #recordLoaded}）。
     *
     * @param identityOrName
     *            资源相对名（{@code collection/id}）或原生 ID
     * @param instance
     *            加载得到的实体实例
     * @param storageVersion
     *            存储版本（null = 该表无版本列 / 旧库 NULL）
     */
    public static void recordObserved(Object identityOrName, Object instance, Long storageVersion) {
        State state = state();
        if (state.current.size() >= State.OBSERVE_LIMIT) {
            if (!state.observationOverflowReported) {
                state.observationOverflowReported = true;
                State.LOGGER.warn("ETag 自动观测登记超过单请求 {} 行上限，停止登记（HTTP 请求级状态随请求清理不触达；非 HTTP 长驻线程请改用显式作用域）",
                        State.OBSERVE_LIMIT);
            }
            return;
        }
        state.loaded.put(instance, storageVersion);
        if (identityOrName != null) {
            String key = String.valueOf(identityOrName);
            state.current.put(key, storageVersion);
            String rawId = rawIdOf(key);
            if (rawId != null && !rawId.equals(key)) {
                state.current.put(rawId, storageVersion);
            }
        }
    }

    /**
     * 仓储保存成功后回写：该实例的版本推进到新值，资源当前版本同步更新；同时<b>消费</b>该资源的 {@code lockedVersion} 期望—— 期望对应的是「保存前的版本」，保存即已兑现，
     * 同请求内的后续加载（如写后重读组装响应）不得再拿陈旧期望比对新版本（否则合法更新恒 409）。 一次写请求对应一次期望兑现。
     */
    public static void recordSaved(Object identityOrName, Object instance, Long newStorageVersion) {
        State state = state();
        state.loaded.put(instance, newStorageVersion);
        if (identityOrName != null) {
            String key = String.valueOf(identityOrName);
            state.current.put(key, newStorageVersion);
            String rawId = rawIdOf(key);
            if (rawId != null && !rawId.equals(key)) {
                state.current.put(rawId, newStorageVersion);
            }
            state.expectationConsumed.add(rawIdOf(key));
        }
    }

    public static void recordSaved(String resourceName, Object instance, Long newStorageVersion) {
        recordSaved((Object) resourceName, instance, newStorageVersion);
    }

    /** 资源当前对外代数（存储版本 + 1；未记录 → null；已记录但版本为 null → 1）。支持 AIP-122 相对名与原生 ID 互查（精确匹配）。 */
    public static Long currentGeneration(Object identityOrName) {
        if (identityOrName == null) {
            return null;
        }
        State state = stateOrNull();
        if (state == null) {
            return null;
        }
        String key = String.valueOf(identityOrName);
        if (!state.current.containsKey(key) && !state.current.containsKey(rawIdOf(key))) {
            return null;
        }
        return EtagSupport.generationOf(currentStorageVersion(identityOrName));
    }

    public static Long currentGeneration(String resourceName) {
        return currentGeneration((Object) resourceName);
    }

    /** 资源当前存储版本原值（未记录 → null）。支持 AIP-122 相对名与原生 ID 互查（精确匹配，无后缀模糊回退）。 */
    public static Long currentStorageVersion(Object identityOrName) {
        if (identityOrName == null) {
            return null;
        }
        State state = stateOrNull();
        if (state == null) {
            return null;
        }
        String key = String.valueOf(identityOrName);
        if (state.current.containsKey(key)) {
            return state.current.get(key);
        }
        String rawId = rawIdOf(key);
        if (state.current.containsKey(rawId)) {
            return state.current.get(rawId);
        }
        return null;
    }

    public static Long currentStorageVersion(String resourceName) {
        return currentStorageVersion((Object) resourceName);
    }

    // ════════ 编程式作用域（非 HTTP 线程 / 单测 / 作业） ════════

    /** 显式建立单资源期望作用域（finally 还原，防线程池泄漏）。支持 AIP-122 相对名或原生 ID。 */
    public static void withExpected(Object identityOrName, Long storageVersion, Runnable action) {
        State state = state();
        String key = String.valueOf(identityOrName);
        String rawId = rawIdOf(key);

        Long previousExpected = state.expected.get(key);
        Long previousExpectedRaw = state.expected.get(rawId);
        boolean hadExpected = state.expected.containsKey(key);
        boolean hadExpectedRaw = state.expected.containsKey(rawId);

        Long previousCurrent = state.current.get(key);
        Long previousCurrentRaw = state.current.get(rawId);
        boolean hadCurrent = state.current.containsKey(key);
        boolean hadCurrentRaw = state.current.containsKey(rawId);

        // 显式作用域 = 一次新的期望：清掉历史消费标记（线程级回退状态跨用例复用，残留会使断言被跳过）
        boolean hadConsumed = state.expectationConsumed.contains(rawId);
        state.expectationConsumed.remove(rawId);

        state.expected.put(key, storageVersion);
        if (rawId != null && !rawId.equals(key)) {
            state.expected.put(rawId, storageVersion);
        }
        try {
            action.run();
        } finally {
            if (hadExpected) {
                state.expected.put(key, previousExpected);
            } else {
                state.expected.remove(key);
            }
            if (rawId != null && !rawId.equals(key)) {
                if (hadExpectedRaw) {
                    state.expected.put(rawId, previousExpectedRaw);
                } else {
                    state.expected.remove(rawId);
                }
            }

            if (hadCurrent) {
                state.current.put(key, previousCurrent);
            } else {
                state.current.remove(key);
            }
            if (rawId != null && !rawId.equals(key)) {
                if (hadCurrentRaw) {
                    state.current.put(rawId, previousCurrentRaw);
                } else {
                    state.current.remove(rawId);
                }
            }
            if (hadConsumed) {
                state.expectationConsumed.add(rawId);
            }
            discardIfEmpty(state);
        }
    }

    public static void withExpected(String resourceName, Long storageVersion, Runnable action) {
        withExpected((Object) resourceName, storageVersion, action);
    }

    /** 清空当前线程回退状态（请求属性由 Spring 清理，无需调用方处理）。 */
    public static void clear() {
        FALLBACK.remove();
    }

    // ════════ 跨线程传播（LRO Worker / 流式导出） ════════

    /**
     * 并发上下文快照（期望版本 / 当前版本 / 已消费期望）。<b>不含</b>按实例身份键控的 CAS 基线——聚合实例不可跨线程序列化， 目标线程重新加载聚合时由观测登记重建基线。
     */
    public record Snapshot(Map<String, Long> expected, Map<String, Long> current,
            java.util.Set<String> expectationConsumed) {
    }

    /** 捕获当前请求 / 线程的上下文快照（无状态 → 空快照）。在<b>发起线程</b>调用。 */
    public static Snapshot capture() {
        State state = stateOrNull();
        if (state == null) {
            return new Snapshot(Map.of(), Map.of(), java.util.Set.of());
        }
        return new Snapshot(Map.copyOf(state.expected), Map.copyOf(state.current),
                java.util.Set.copyOf(state.expectationConsumed));
    }

    /**
     * 在<b>目标线程</b>恢复快照（合并进该线程上下文；HTTP 请求线程写入请求属性，否则写入线程级回退状态）。 作业结束须 {@link #clear()}（非 HTTP 线程）防线程池残留。
     */
    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        State state = state();
        state.expected.putAll(snapshot.expected());
        state.current.putAll(snapshot.current());
        state.expectationConsumed.addAll(snapshot.expectationConsumed());
    }

    // ════════ 内部 ════════

    private static final class State {

        /** 自动观测登记的行数上限（非 HTTP 长驻线程的内存兜底）。 */
        static final int OBSERVE_LIMIT = 8192;

        static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EtagContext.class);

        /** 期望版本（按资源相对名）。 */
        final Map<String, Long> expected = new HashMap<>();
        /** 资源当前版本（按资源相对名）。 */
        final Map<String, Long> current = new HashMap<>();
        /** 加载版本 / CAS 基线（按聚合实例身份——同 id 的不同实例互不覆盖）。 */
        final Map<Object, Long> loaded = new IdentityHashMap<>();
        /** 已兑现期望的资源（原生 ID）：保存即消费，同请求后续加载不再断言。 */
        final java.util.Set<String> expectationConsumed = new java.util.HashSet<>();
        /** 超限告警只报一次。 */
        boolean observationOverflowReported;

        boolean isEmpty() {
            return expected.isEmpty() && current.isEmpty() && loaded.isEmpty() && expectationConsumed.isEmpty();
        }
    }

    /** 回退状态空了就释放（避免线程池长期持有）。 */
    private static void discardIfEmpty(State state) {
        if (FALLBACK.get() == state && state.isEmpty()) {
            FALLBACK.remove();
        }
    }

    private static State state() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            State state = (State) attributes.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (state == null) {
                state = new State();
                attributes.setAttribute(REQUEST_ATTRIBUTE, state, RequestAttributes.SCOPE_REQUEST);
            }
            return state;
        }
        State state = FALLBACK.get();
        if (state == null) {
            state = new State();
            FALLBACK.set(state);
        }
        return state;
    }

    private static State stateOrNull() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return (State) attributes.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        }
        return FALLBACK.get();
    }

    static String rawIdOf(Object identityOrName) {
        if (identityOrName == null) {
            return null;
        }
        String s = String.valueOf(identityOrName);
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    /**
     * 聚合是否已软删（回收站行对版本断言不可见——单体 GET 照常 200，不以并发前置拦截）。 探测常见软删访问器并按返回类型判读： 标志族（{@code isDeleted} /
     * {@code getDeleteFlag}）——{@code Boolean.TRUE} 或数值等于已删标志值；时刻族（{@code getDeleteTime} / {@code getDeleteTimeMs}）——非
     * null 即已删。其他表达方式的领域对象会被视为存活。
     */
    private static boolean isSoftDeleted(Object instance) {
        if (instance == null) {
            return false;
        }
        for (String accessor : new String[]{"isDeleted", "getDeleteFlag", "getDeleteTime", "getDeleteTimeMs"}) {
            try {
                Method method = instance.getClass().getMethod(accessor);
                Object res = method.invoke(instance);
                boolean deleted;
                if (res instanceof Boolean flag) {
                    deleted = flag;
                } else if (res instanceof Number number) {
                    deleted = number.intValue() == SoftDeleteContext.deletedValue();
                } else {
                    deleted = res != null;
                }
                if (deleted) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }
}
