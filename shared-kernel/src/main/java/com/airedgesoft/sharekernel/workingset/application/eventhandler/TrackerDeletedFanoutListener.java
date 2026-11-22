package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerBindingRemovalRequestedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerDeletedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerDeletionRequestedEvent;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tracker 删除后，把解绑工作拆成每个 Working Set 一个独立事件。
 */
@Component
@RequiredArgsConstructor
public class TrackerDeletedFanoutListener {
    private final WorkingSetQueryProvider queries;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener(id = "bootstrap.TrackerDeletedFanoutListener.on(sk.workingset.domain.event.TrackerDeletionRequestedEvent)")
    public void on(TrackerDeletionRequestedEvent event) {
        List<Long> bound = queries.findWorkingSetIdsBoundToTracker(event.trackerId().numericValue());
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
