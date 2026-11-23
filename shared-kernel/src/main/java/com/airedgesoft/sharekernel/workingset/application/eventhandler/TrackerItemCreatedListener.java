package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.TrackerItemCommandService;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemCreatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TrackerItemCreatedListener {

    private final TrackerItemCommandService lifecycle;

    public TrackerItemCreatedListener(TrackerItemCommandService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @ApplicationModuleListener(id = "bootstrap.TrackerItemCreatedListener.on(sk.workingset.domain.event.TrackerItemCreatedEvent)")
    public void on(TrackerItemCreatedEvent event) {
        lifecycle.registerCreated(event);
    }
}
