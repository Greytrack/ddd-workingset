package com.airedgesoft.sharedkernel.workingset.application.eventhandler;

import com.airedgesoft.sharedkernel.workingset.application.command.LightTrackerItemCommandService;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectLocalizedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackerItemLocalizedListener {

    private final LightTrackerItemCommandService lifecycle;

    @ApplicationModuleListener
    public void on(BusinessObjectLocalizedEvent event) {
        lifecycle.completeInheritedEdit(event.localId(), event.revision());
    }
}
