package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.TrackerItemLifecycleService;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectDeleteEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackerItemDeletionChangedListener {
    private final TrackerItemLifecycleService lifecycle;

    @ApplicationModuleListener
    public void on(BusinessObjectDeleteEvent event) {
        lifecycle.synchronizeDeletion(event.localId());
    }
}
