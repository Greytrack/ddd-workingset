package com.airedgesoft.sharedkernel.workingset.application.eventhandler;

import com.airedgesoft.sharedkernel.workingset.application.command.CowMaterializationCompletionService;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationCompletedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 监听 ALM 的实例化结果，成功时切换 LTI，失败结果保留在事件发布记录中。
 */
@Component
public class TrackerItemMaterializationCompletedListener {
    private final CowMaterializationCompletionService completion;

    public TrackerItemMaterializationCompletedListener(CowMaterializationCompletionService completion) {
        this.completion = completion;
    }

    @ApplicationModuleListener(id = "bootstrap.TrackerItemMaterializationCompletedListener.on(sk.workingset.domain.event.TrackerItemMaterializationCompletedEvent)")
    public void on(TrackerItemMaterializationCompletedEvent event) {
        completion.complete(event);
    }
}
