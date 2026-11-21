package sk.workingset.application;

import sk.workingset.domain.lighttrackeritem.ItemClassification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.CowEventHandler;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.event.SnapshotRefEvent;
import sk.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.cow.CowMaterializationStore;
import sk.workingset.domain.workingset.WorkingSetId;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CowEventHandlerTest {
    private static LightTrackerItem target(String id, LocalId source, Revision own, Revision sourceRevision) {
        return LightTrackerItem.restore(LocalId.of(id), CommonId.of("264220103598016"), TrackerId.of("264443044134848"),
                WorkingSetId.of("266514385749952"), own, source, sourceRevision, false, new ItemClassification(42L, 14010101L));
    }

    @Test
    void changed_source_publishes_a_materialization_request_for_each_unmaterialized_target() {
        LightTrackerItemRepository items = mock(LightTrackerItemRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        LocalId source = LocalId.of("261802623213504");
        Revision before = Revision.of(3);
        Revision after = Revision.of(4);
        Revision own = Revision.of(1);
        LightTrackerItem target = target("258520000000001", source, own, before);
        when(items.findSourceBackedTargets(source, before)).thenReturn(List.of(target));

        BusinessObjectUpdateEvent event = new BusinessObjectUpdateEvent(42, source, source, before, after,
                LockVersion.of(5), LockVersion.of(6), SnapshotRefEvent.of("258782756974528"), Instant.EPOCH);

        CowMaterializationStore store = mock(CowMaterializationStore.class);
        CowMaterializationStore.Entry pending = new CowMaterializationStore.Entry(42, target.localId(),
                source, before, CowMaterializationStore.Status.PENDING, 1, false, null);
        when(store.markPending(42, target.localId(), source, before)).thenReturn(pending);

        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenAnswer(invocation ->
                ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0))
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
