package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.LightTrackerItemCommandService;
import com.airedgesoft.sharekernel.workingset.domain.event.CompleteDeleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 业务对象永久删除后，由 SK 移除对应 LightTrackerItem（D4：跨聚合用事件交互）。
 * <p>
 * 处理失败时事件保留为未完成，不再提供 COW 恢复入口；
 * 处理本身幂等（无对应 LTI 为空操作）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackerItemCompleteDeletedListener {

    private final LightTrackerItemCommandService lightTrackerItemCommandService;

    @ApplicationModuleListener(id = "bootstrap.RequirementPurgedListener.on(sk.workingset.domain.event.CompleteDeleteEvent)")
    public void on(CompleteDeleteEvent event) {
        lightTrackerItemCommandService.removePurged(event.requirementId());
    }
}
