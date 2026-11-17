package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.WorkingSetCreationService;
import sk.workingset.application.event.WorkingSetCreationSubmitted;

@Component
public class WorkingSetCreationSubmittedListener {
    private final WorkingSetCreationService creations;

    public WorkingSetCreationSubmittedListener(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    @ApplicationModuleListener(id = "bootstrap.WorkingSetCreationSubmittedListener.on(sk.workingset.application.event.WorkingSetCreationSubmitted)")
    public void on(WorkingSetCreationSubmitted event) {
        creations.execute(event.creationId());
    }
}
