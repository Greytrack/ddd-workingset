package sk.workingset.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.WorkingSetBindingRemovalService;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.event.TrackerBindingRemovalRequestedEvent;
import sk.workingset.domain.event.TrackerDeletedEvent;

/** 每个事件只解绑一个 Working Set 聚合。 */
@Component
@RequiredArgsConstructor
public class TrackerBindingRemovalListener {
    private final WorkingSetBindingRemovalService removals;
    private final WorkingSetQueryProvider queries;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener(id = "bootstrap.TrackerBindingRemovalListener.on(sk.workingset.domain.event.TrackerBindingRemovalRequestedEvent)")
    public void on(TrackerBindingRemovalRequestedEvent event) {
        removals.remove(event.workingSetId(), event.trackerId());
        if (queries.findWorkingSetIdsBoundToTracker(event.trackerId().numericValue()).isEmpty()) {
            events.publishEvent(new TrackerDeletedEvent(event.trackerId()));
        }
    }
}
