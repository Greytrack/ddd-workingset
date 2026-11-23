package com.airedgesoft.sharedkernel.workingset.application;


import com.airedgesoft.sharedkernel.workingset.application.command.CowEventHandler;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowRequestPublications;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import java.util.function.Consumer;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CowEventHandlerTest {
    private static LightTrackerItem target(String id, Long source, Integer own, Integer sourceRevision) {
        return LightTrackerItem.restore(Long.valueOf(id), Long.valueOf("264220103598016"), Long.valueOf("264443044134848"),
                Long.valueOf("266514385749952"), own, source, sourceRevision, false, 42L, 14010101L);
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
                Long.valueOf(5), Long.valueOf(6), JsonNodeFactory.instance.objectNode(), 0L);

        CowRequestPublications publications = mock(CowRequestPublications.class);
        when(items.findByIdForUpdate(target.localId())).thenReturn(Optional.of(target));
        when(publications.exists(source, target.localId())).thenReturn(false, true);

        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());

        CowEventHandler handler = new CowEventHandler(items, transaction, publications, events);
        handler.onTrackerItemChanged(event);
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
