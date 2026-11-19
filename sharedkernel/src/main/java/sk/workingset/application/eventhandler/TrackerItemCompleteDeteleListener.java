package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.TrackerItemLifecycleService;
import sk.workingset.domain.event.CompleteDeleteEvent;

/**
 * 业务对象永久删除后，由 SK 移除对应 LightTrackerItem（D4：跨聚合用事件交互）。
 * <p>
 * 处理失败时事件保留为未完成，不再提供 COW 恢复入口；
 * 处理本身幂等（无对应 LTI 为空操作）。
 */
@Component
public class TrackerItemCompleteDeteleListener {

    private final TrackerItemLifecycleService trackerItemLifecycleService;

    public TrackerItemCompleteDeteleListener(TrackerItemLifecycleService lifecycle) {
        this.trackerItemLifecycleService = lifecycle;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementPurgedListener.on(sk.workingset.domain.event.CompleteDeleteEvent)")
    public void on(CompleteDeleteEvent event) {
        trackerItemLifecycleService.removePurged(event.requirementId());
    }
}
