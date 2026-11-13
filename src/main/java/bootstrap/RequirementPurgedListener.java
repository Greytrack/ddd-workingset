package bootstrap;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.event.RequirementPurged;
import sk.workingset.application.port.TrackerItemLifecyclePort;

/**
 * 业务对象永久删除后，由 SK 移除对应 LightTrackerItem（D4：跨聚合用事件交互）。
 *
 * <p>D13：与 {@link RequirementChangedListener} 一致走 Modulith 事件发布记录——
 * 处理失败时事件保留为未完成，可通过 `/api/cow/recover/incomplete` 重投；
 * 处理本身幂等（无对应 LTI 为空操作）。
 */
@Component
public class RequirementPurgedListener {

    private final TrackerItemLifecyclePort lifecycle;

    public RequirementPurgedListener(TrackerItemLifecyclePort lifecycle) {
        this.lifecycle = lifecycle;
    }

    @ApplicationModuleListener
    public void on(RequirementPurged event) {
        lifecycle.removePurged(event.requirementId());
    }
}
