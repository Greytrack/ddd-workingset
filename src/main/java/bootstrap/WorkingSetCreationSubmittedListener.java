package bootstrap;

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

    @ApplicationModuleListener
    public void on(WorkingSetCreationSubmitted event) {
        creations.execute(event.creationId());
    }
}
