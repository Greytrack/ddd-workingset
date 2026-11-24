package com.airedgesoft.sharedkernel.businessframework.application.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.threadpool.TtlExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 授权执行线程上下文（基于阿里 {@link TransmittableThreadLocal}）：
 * <ul>
 * <li>承载当前线程（及异步子线程）的 {@code userId} / {@code workspaceId} / {@code workingsetId} 覆盖值；</li>
 * <li>未设置显式覆盖时回落已安装的回退读取器（例如由 {@code bridge-v3} 接入旧系统）；</li>
 * <li>提供 {@link #withWorkspace(Long)} / {@link #withContext(Long, Long, Long)} 作用域，退出 {@code try-with-resources}
 * 时精确恢复前值，防止线程池复用串上下文；</li>
 * <li>提供 {@link #wrap(Runnable)} / {@link #wrap(Callable)} / {@link #wrap(ExecutorService)}，通过 TTL 自动透传跨线程上下文。</li>
 * </ul>
 */
public final class AuthorizationScope {

    private static final TransmittableThreadLocal<Long> USER_ID = nonInheritingTtl();
    private static final TransmittableThreadLocal<Long> WORKSPACE_ID = nonInheritingTtl();
    private static final TransmittableThreadLocal<Long> WORKINGSET_ID = nonInheritingTtl();

    /**
     * 创建不向新建子线程隐式继承的 TTL：防止线程池在首次提交任务时懒创建工作线程把调用方上下文永久固化为工作线程初值；跨线程透传统一由 {@link TtlRunnable} / {@link TtlExecutors}
     * 显式捕获并在结束后精准恢复。
     */
    private static TransmittableThreadLocal<Long> nonInheritingTtl() {
        return new TransmittableThreadLocal<>() {
            @Override
            protected Long childValue(Long parentValue) {
                return null;
            }
        };
    }

    private static final long STANDALONE_USER_ID = 419436436619264L;
    private static volatile ContextFallback fallback = ContextFallback.EMPTY;

    private AuthorizationScope() {
    }

    /** 安装底层线程上下文回退源，显式替换当前回退源。 */
    public static synchronized void installFallback(ContextFallback contextFallback) {
        fallback = contextFallback == null ? ContextFallback.EMPTY : contextFallback;
    }

    /** 仅在尚未注册自定义回退源（仍为 {@link ContextFallback#EMPTY}）时安装，避免多次构造 Bean 互相覆盖（L3）。 */
    public static synchronized boolean installFallbackIfAbsent(ContextFallback contextFallback) {
        if (fallback != ContextFallback.EMPTY || contextFallback == null) {
            return false;
        }
        fallback = contextFallback;
        return true;
    }

    public static Long currentUserId() {
        Long override = USER_ID.get();
        if (override != null) {
            return override;
        }
        ContextFallback source = fallback;
        // Standalone demo and background processing retain the existing default actor.
        return source == ContextFallback.EMPTY ? Long.valueOf(STANDALONE_USER_ID) : source.currentUserId();
    }

    public static Long currentWorkspaceId() {
        Long override = WORKSPACE_ID.get();
        return override != null ? override : fallback.currentWorkspaceId();
    }

    public static Long currentWorkingsetId() {
        Long override = WORKINGSET_ID.get();
        return override != null ? override : fallback.currentWorkingsetId();
    }

    /** 临时覆写当前线程的 {@code workspaceId}（不改 userId / workingsetId），关闭时恢复原值。 */
    public static Scope withWorkspace(Long workspaceId) {
        Long previous = WORKSPACE_ID.get();
        if (workspaceId == null) {
            WORKSPACE_ID.remove();
        } else {
            WORKSPACE_ID.set(workspaceId);
        }
        return () -> restore(WORKSPACE_ID, previous);
    }

    /** 临时覆写当前线程的完整授权上下文，关闭时精确恢复原值。 */
    public static Scope withContext(Long userId, Long workspaceId, Long workingsetId) {
        Long prevUser = USER_ID.get();
        Long prevWorkspace = WORKSPACE_ID.get();
        Long prevWorkingset = WORKINGSET_ID.get();
        apply(USER_ID, userId);
        apply(WORKSPACE_ID, workspaceId);
        apply(WORKINGSET_ID, workingsetId);
        return () -> {
            restore(USER_ID, prevUser);
            restore(WORKSPACE_ID, prevWorkspace);
            restore(WORKINGSET_ID, prevWorkingset);
        };
    }

    /** 捕获当前线程上下文快照，用于跨线程切换时显式透传并在任务完成后精确还原。 */
    public static Runnable wrap(Runnable task) {
        Long userId = currentUserId();
        Long workspaceId = currentWorkspaceId();
        Long workingsetId = currentWorkingsetId();
        return () -> {
            try (Scope ignored = withContext(userId, workspaceId, workingsetId)) {
                task.run();
            }
        };
    }

    /** 捕获当前线程上下文快照，用于跨线程切换时显式透传并在任务完成后精确还原。 */
    public static <V> Callable<V> wrap(Callable<V> task) {
        Long userId = currentUserId();
        Long workspaceId = currentWorkspaceId();
        Long workingsetId = currentWorkingsetId();
        return () -> {
            try (Scope ignored = withContext(userId, workspaceId, workingsetId)) {
                return task.call();
            }
        };
    }

    /** 捕获当前线程上下文快照，在作用域内执行并返回结果。 */
    public static <T> T supplyInWorkspace(Long workspaceId, Supplier<T> supplier) {
        try (Scope ignored = withWorkspace(workspaceId)) {
            return supplier.get();
        }
    }

    /** 使用 TTL 装饰线程池，使所有提交任务自动透传 {@link TransmittableThreadLocal} 上下文。 */
    public static ExecutorService wrap(ExecutorService executor) {
        return TtlExecutors.getTtlExecutorService(executor);
    }

    private static void apply(TransmittableThreadLocal<Long> holder, Long value) {
        if (value == null) {
            holder.remove();
        } else {
            holder.set(value);
        }
    }

    private static void restore(TransmittableThreadLocal<Long> holder, Long previous) {
        if (previous == null) {
            holder.remove();
        } else {
            holder.set(previous);
        }
    }

    /** 无受检异常的作用域句柄，便于 try-with-resources 使用。 */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /** 回退读取契约。 */
    public interface ContextFallback {

        ContextFallback EMPTY = new ContextFallback() {
            @Override
            public Long currentUserId() {
                return null;
            }

            @Override
            public Long currentWorkspaceId() {
                return null;
            }

            @Override
            public Long currentWorkingsetId() {
                return null;
            }
        };

        Long currentUserId();

        Long currentWorkspaceId();

        Long currentWorkingsetId();
    }
}
