package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.WorkingSetCreationService;
import sk.workingset.domain.event.WorkingSetCreationSubmittedEvent;

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
