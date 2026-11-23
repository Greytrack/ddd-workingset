package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.LightTrackerItemCommandService;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectDeleteEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackerItemDeletionChangedListener {
    private final LightTrackerItemCommandService lifecycle;

    @ApplicationModuleListener
    public void on(BusinessObjectDeleteEvent event) {
        lifecycle.synchronizeDeletion(event.localId());
    }
}
