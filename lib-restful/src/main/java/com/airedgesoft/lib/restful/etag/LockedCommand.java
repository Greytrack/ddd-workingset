package com.airedgesoft.lib.restful.etag;

/**
 * 携带并发版本的写命令（AIP-154 强一致变体，项目偏离 D13）：所有状态变更写请求的 body 必须显式携带 {@code lockedVersion}。
 *
 * <p>
 * <b>职责边界</b>：{@code lockedVersion} 只做并发控制，载体是 <b>body</b>；{@code ETag} 只做缓存校验，载体是 <b>响应 / 请求头</b>。二者不再混用（规范 §6）。
 *
 * <p>
 * <b>框架如何识别写请求</b>：{@link PreconditionAspect} 只对 body 实现本接口的端点做强制校验——Create 命令与 删除 /
 * 恢复族命令<b>不实现</b>本接口，因而天然豁免，无需端点再声明额外注解。
 */
public interface LockedCommand {

    /**
     * 对外并发代数（从 1 起算 = 存储 {@code lock_version} + 1）。
     *
     * @return 版本代数；缺失由框架判 400（{@code MISSING_LOCKED_VERSION}）
     */
    Long lockedVersion();
}
