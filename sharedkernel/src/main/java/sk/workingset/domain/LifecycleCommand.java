package sk.workingset.domain;

import java.util.Objects;

/**
 * 生命周期能力调用参数（D1/D14）：只承载领域可观察的期望证据与业务原因。
 *
 * <p>不携带应用层调用者上下文；授权由应用层在调用前完成（D11 暂不接入权限）。
 *
 * @param expectedLockVersion 期望的并发版本；{@code null} 表示调用方已持有该对象的锁
 * @param reason              业务原因，可为空
 */
public record LifecycleCommand(LockVersion expectedLockVersion, String reason) {

    public LifecycleCommand {
        reason = reason == null ? "" : reason;
    }

    /** 调用方已锁定对象时使用。 */
    public static LifecycleCommand locked(String reason) {
        return new LifecycleCommand(null, reason);
    }

    public static LifecycleCommand of(LockVersion expectedLockVersion) {
        return new LifecycleCommand(Objects.requireNonNull(expectedLockVersion, "expectedLockVersion"), "");
    }
}
