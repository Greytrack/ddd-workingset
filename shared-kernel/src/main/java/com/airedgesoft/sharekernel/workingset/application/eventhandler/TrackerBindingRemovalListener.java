package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.WorkingSetBindingRemovalService;
import com.airedgesoft.sharekernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerBindingRemovalRequestedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 每个事件只解绑一个 Working Set 聚合。
 */
@Component
@RequiredArgsConstructor
public class TrackerBindingRemovalListener {
    private final WorkingSetBindingRemovalService removals;
    private final WorkingSetQueryProvider queries;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener(id = "bootstrap.TrackerBindingRemovalListener.on(sk.workingset.domain.event.TrackerBindingRemovalRequestedEvent)")
    public void on(TrackerBindingRemovalRequestedEvent event) {
        removals.remove(event.workingSetId(), event.trackerId());
        if (queries.findWorkingSetIdsBoundToTracker(event.trackerId()).isEmpty()) {
            events.publishEvent(new TrackerDeletedEvent(event.trackerId()));
        }
    }
}
