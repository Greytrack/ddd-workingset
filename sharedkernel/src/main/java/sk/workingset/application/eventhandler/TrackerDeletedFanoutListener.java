package sk.workingset.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.event.TrackerBindingRemovalRequestedEvent;
import sk.workingset.domain.event.TrackerDeletionRequestedEvent;
import sk.workingset.domain.event.TrackerDeletedEvent;

/** Tracker 删除后，把解绑工作拆成每个 Working Set 一个独立事件。 */
@Component
@RequiredArgsConstructor
public class TrackerDeletedFanoutListener {
    private final WorkingSetQueryProvider queries;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener(id = "bootstrap.TrackerDeletedFanoutListener.on(sk.workingset.domain.event.TrackerDeletionRequestedEvent)")
    public void on(TrackerDeletionRequestedEvent event) {
        var bound = queries.findWorkingSetIdsBoundToTracker(event.trackerId().numericValue());
        if (bound.isEmpty()) {
            events.publishEvent(new TrackerDeletedEvent(event.trackerId()));
            return;
        }
        for (Long workingSetId : bound) {
            events.publishEvent(new TrackerBindingRemovalRequestedEvent(
                    WorkingSetId.of(workingSetId), event.trackerId()));
        }
    }
}
