package com.airedgesoft.sharedkernel.workingset.application.eventhandler;

import com.airedgesoft.sharedkernel.workingset.application.command.LightTrackerItemCommandService;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TrackerItemCreatedListener {

    private final LightTrackerItemCommandService lifecycle;

    public TrackerItemCreatedListener(LightTrackerItemCommandService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @ApplicationModuleListener(id = "bootstrap.TrackerItemCreatedListener.on(sk.workingset.domain.event.TrackerItemCreatedEvent)")
    public void on(TrackerItemCreatedEvent event) {
        lifecycle.registerCreated(event);
    }
}
