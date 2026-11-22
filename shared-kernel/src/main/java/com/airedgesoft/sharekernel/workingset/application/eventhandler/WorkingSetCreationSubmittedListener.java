package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.WorkingSetCreationService;
import com.airedgesoft.sharekernel.workingset.domain.event.WorkingSetCreationSubmittedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class WorkingSetCreationSubmittedListener {
    private final WorkingSetCreationService creations;

    public WorkingSetCreationSubmittedListener(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    @ApplicationModuleListener(id = "bootstrap.WorkingSetCreationSubmittedListener.on(sk.workingset.domain.event.WorkingSetCreationSubmittedEvent)")
    public void on(WorkingSetCreationSubmittedEvent event) {
        creations.execute(event.creationId());
    }
}
