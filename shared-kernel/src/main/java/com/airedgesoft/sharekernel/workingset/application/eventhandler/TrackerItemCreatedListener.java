package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.TrackerItemLifecycleService;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TrackerItemCreatedListener {

    private final TrackerItemLifecycleService lifecycle;

    public TrackerItemCreatedListener(TrackerItemLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @ApplicationModuleListener(id = "bootstrap.TrackerItemCreatedListener.on(sk.workingset.domain.event.TrackerItemCreatedEvent)")
    public void on(TrackerItemCreatedEvent event) {
        lifecycle.registerCreated(event);
    }
}
