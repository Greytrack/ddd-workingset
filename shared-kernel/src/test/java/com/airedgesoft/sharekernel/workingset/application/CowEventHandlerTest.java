package com.airedgesoft.sharekernel.workingset.application;


import com.airedgesoft.sharekernel.workingset.application.command.CowEventHandler;
import com.airedgesoft.sharekernel.workingset.domain.cow.CowMaterializationStore;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.SnapshotRefEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.ItemClassification;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CowEventHandlerTest {
    private static LightTrackerItem target(String id, Long source, Integer own, Integer sourceRevision) {
        return LightTrackerItem.restore(Long.valueOf(id), Long.valueOf("264220103598016"), Long.valueOf("264443044134848"),
                Long.valueOf("266514385749952"), own, source, sourceRevision, false, new ItemClassification(42L, 14010101L));
    }

    @Test
    void changed_source_publishes_a_materialization_request_for_each_unmaterialized_target() {
        LightTrackerItemRepository items = mock(LightTrackerItemRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Long source = Long.valueOf("261802623213504");
        Integer before = 3;
        Integer after = 4;
        Integer own = 1;
        LightTrackerItem target = target("258520000000001", source, own, before);
        when(items.findSourceBackedTargets(source, before)).thenReturn(List.of(target));

        BusinessObjectUpdateEvent event = new BusinessObjectUpdateEvent(42, source, source, before, after,
                Long.valueOf(5), Long.valueOf(6), SnapshotRefEvent.of("258782756974528"), 0L);

        CowMaterializationStore store = mock(CowMaterializationStore.class);
        CowMaterializationStore.Entry pending = new CowMaterializationStore.Entry(42, target.localId(),
                source, before, CowMaterializationStore.Status.PENDING, 1, false, null);
        when(store.markPending(42, target.localId(), source, before)).thenReturn(pending);

        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));

        CowEventHandler handler = new CowEventHandler(items, transaction, store, events);
        handler.onTrackerItemChanged(event);

        ArgumentCaptor<TrackerItemMaterializationRequestedEvent> request =
                ArgumentCaptor.forClass(TrackerItemMaterializationRequestedEvent.class);
        verify(events).publishEvent(request.capture());
        assertEquals(before, request.getValue().beforeRevision());
        assertEquals(after, request.getValue().afterRevision());
        assertEquals(own, request.getValue().targetRevision());
        assertEquals(target.localId(), request.getValue().targetLocalId());
    }
}
