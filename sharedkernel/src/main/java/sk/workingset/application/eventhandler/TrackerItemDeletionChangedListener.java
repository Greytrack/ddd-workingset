package sk.workingset.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.TrackerItemLifecycleService;
import sk.workingset.domain.event.BusinessObjectDeleteEvent;

@Component
@RequiredArgsConstructor
public class TrackerItemDeletionChangedListener {
    private final TrackerItemLifecycleService lifecycle;

    @ApplicationModuleListener
    public void on(BusinessObjectDeleteEvent event) {
        lifecycle.synchronizeDeletion(event.localId());
    }
}
