package bootstrap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import sk.workingset.application.event.RequirementPurged;
import sk.workingset.application.port.TrackerItemLifecyclePort;

/**
 * 业务对象永久删除后，由 SK 移除对应 LightTrackerItem（D4：跨聚合用事件交互）。
 *
 * <p>在删除事务提交后同步执行，保证删除响应返回时 LTI 已完成清理；
 * 处理幂等（重复事件或无对应 LTI 均为空操作）。
 */
@Component
public class RequirementPurgedListener {

    private final TrackerItemLifecyclePort lifecycle;

    public RequirementPurgedListener(TrackerItemLifecyclePort lifecycle) {
        this.lifecycle = lifecycle;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RequirementPurged event) {
        lifecycle.removePurged(event.requirementId());
    }
}
