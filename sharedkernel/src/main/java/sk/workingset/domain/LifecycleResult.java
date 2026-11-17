package sk.workingset.domain;

import java.util.Objects;

/**
 * 生命周期能力结果（D1/D14）：不把拒绝或冲突伪装成成功。
 *
 * @param status 结果状态
 * @param reason 拒绝/冲突原因；成功时为空
 * @param object 成功时的变换后对象；{@code delete2} 返回当前对象表示“允许由应用服务编排永久删除”
 */
public record LifecycleResult(Status status, String reason, BusinessObject object) {

    public enum Status { Applied, Rejected, Conflict }

    public LifecycleResult {
        Objects.requireNonNull(status, "status");
        reason = reason == null ? "" : reason;
    }

    public static LifecycleResult applied(BusinessObject object) {
        return new LifecycleResult(Status.Applied, "", object);
    }

    public static LifecycleResult rejected(String reason) {
        return new LifecycleResult(Status.Rejected, reason, null);
    }

    public static LifecycleResult conflict(String reason) {
        return new LifecycleResult(Status.Conflict, reason, null);
    }

    public boolean succeeded() {
        return status == Status.Applied;
    }
}
