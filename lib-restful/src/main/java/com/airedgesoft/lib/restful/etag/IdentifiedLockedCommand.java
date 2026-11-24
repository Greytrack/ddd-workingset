package com.airedgesoft.lib.restful.etag;

/**
 * 带资源标识的并发版本命令：批量写请求的每个条目都必须携带 {@code id} 与 {@code lockedVersion}。
 *
 * <p>
 * 批量端点（{@code :batchUpdate} / {@code :batchCheckout} / {@code :batchApprove} …）的逐条版本校验依赖该接口： 框架按 {@link #id()}
 * 将期望版本绑定到对应资源，任一条缺失 → 400，任一条不符 → 409，且整批原子回滚。
 */
public interface IdentifiedLockedCommand extends LockedCommand {

    /** 资源标识（裸 id 或 AIP-122 相对资源名）。 */
    String id();
}
