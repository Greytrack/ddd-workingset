package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.TrackerItemLifecycleService;
import sk.workingset.domain.event.TrackerItemCreatedEvent;

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
