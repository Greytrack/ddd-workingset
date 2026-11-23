package com.airedgesoft.sharedkernel.workingset.application.eventhandler;

import com.airedgesoft.sharedkernel.workingset.application.command.CowEventHandler;
import com.airedgesoft.sharedkernel.workingset.application.command.SourceSerialCowCoordinator;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectUpdateEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 在来源提交后执行 COW，并以处理结果决定 Modulith 发布记录是否完成。
 */
@Component
public class TrackerItemChangedListener {

    private final SourceSerialCowCoordinator coordinator;
    private final CowEventHandler handler;

    public TrackerItemChangedListener(SourceSerialCowCoordinator coordinator, CowEventHandler handler) {
        this.coordinator = coordinator;
        this.handler = handler;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementChangedListener.on(sk.workingset.domain.event.BusinessObjectUpdateEvent)")
    public void on(BusinessObjectUpdateEvent event) {
        coordinator.runSerial(event.sourceLocalId(), event.afterLockVersion(),
                () -> handler.onTrackerItemChanged(event));
    }
}
