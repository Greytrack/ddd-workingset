package bootstrap;


import com.airedgesoft.alm.requirement.application.command.RequirementCommandService;
import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证来源修改命令与仓储、快照和发布端口的协作。
 */
class RequirementChangeFlowTest {

    private static final Long L0 = Long.valueOf("260574482192320");
    private static final Integer R3 = 3;

    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TrackerRepository trackers = mock(TrackerRepository.class);
    private final WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
    private final RequirementCommandService service = new RequirementCommandService(
            requirements, new ObjectMapper(), publisher,
            trackers, workingSets,
            mock(LightTrackerItemRepository.class));
    private final Requirement before = Requirement.create(
            L0, Long.valueOf("265506033508288"), Long.valueOf("264905365135296"), "A", "source description", R3, "1.2");

    private static void assertEvent(BusinessObjectUpdateEvent event, String title, long beforeLock, long afterLock) {
        assertEquals(R3, event.beforeRevision());
        assertEquals(R3, event.afterRevision());
        assertEquals(beforeLock, event.beforeLockVersion());
        assertEquals(afterLock, event.afterLockVersion());
        assertEquals(title, event.snapshot().path("title").asText());
        assertEquals(beforeLock, event.snapshot().path("lockVersion").asLong());
        assertEquals(R3.intValue(), event.snapshot().path("revision").asInt());
        assertEquals("1.2", event.snapshot().path("version").asText());
    }

    @BeforeEach
    void setUp() {
        when(requirements.findById(L0)).thenReturn(Optional.of(before));
        Tracker tracker = mock(Tracker.class);
        when(tracker.workingSetId()).thenReturn(WorkingSet.DEFAULT_ID);
        when(trackers.findById(before.trackerId())).thenReturn(Optional.of(tracker));
        when(workingSets.findById(WorkingSet.DEFAULT_ID)).thenReturn(Optional.of(mock(WorkingSet.class)));
    }

    @Test
    void rejectsBusinessEditingBeforeOwningWorkingSetIsReady() {
        when(workingSets.findById(WorkingSet.DEFAULT_ID))
                .thenThrow(new DomainException(FailureReason.PREPARING, "1"));
        DomainException rejected = assertThrows(DomainException.class,
                () -> service.changeTitle(L0, "B", Long.valueOf(0)));
        assertEquals(FailureReason.PREPARING, rejected.getResCode());
        verify(requirements, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void AT03_change_title_keeps_revision_and_version_but_increments_lock_version() {
        Requirement changed = service.changeTitle(L0, "B", Long.valueOf(0));

        assertEquals("B", changed.title());
        assertEquals(R3, changed.revision());
        assertEquals("1.2", changed.version());
        assertEquals(Long.valueOf(1), changed.lockVersion());
        verify(requirements).save(changed);

        ArgumentCaptor<BusinessObjectUpdateEvent> events = ArgumentCaptor.forClass(BusinessObjectUpdateEvent.class);
        verify(publisher).publishEvent(events.capture());
        BusinessObjectUpdateEvent event = events.getValue();
        assertTrue(event.eventId() > 0);
        assertEquals(L0, event.requirementId());
        assertEquals(L0, event.sourceLocalId());
        assertEvent(event, "A", 0, 1);
        assertNotNull(event.occurredAt());

    }

    @Test
    void event_is_published_after_the_repository_saves_the_changed_requirement() {
        Requirement changed = service.changeTitle(L0, "B", Long.valueOf(0));

        InOrder order = inOrder(requirements, publisher);
        order.verify(requirements).save(changed);
        order.verify(publisher).publishEvent(any(BusinessObjectUpdateEvent.class));
    }

    @Test
    void stale_expected_lock_version_is_rejected_without_event_or_snapshot() {
        LockVersionConflictException conflict = assertThrows(LockVersionConflictException.class,
                () -> service.changeTitle(L0, "B", Long.valueOf(99)));

        assertEquals(Long.valueOf(99), conflict.expected());
        assertEquals(Long.valueOf(0), conflict.actual());
        verify(requirements, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void consecutive_changes_publish_ordered_events_and_capture_each_pre_change_snapshot() {
        Requirement first = service.changeTitle(L0, "B", Long.valueOf(0));
        when(requirements.findById(L0)).thenReturn(Optional.of(first));
        Requirement second = service.changeTitle(L0, "C", Long.valueOf(1));

        ArgumentCaptor<BusinessObjectUpdateEvent> events = ArgumentCaptor.forClass(BusinessObjectUpdateEvent.class);
        verify(publisher, times(2)).publishEvent(events.capture());
        assertEvent(events.getAllValues().get(0), "A", 0, 1);
        assertEvent(events.getAllValues().get(1), "B", 1, 2);
        assertTrue(events.getAllValues().get(0).eventId() < events.getAllValues().get(1).eventId());
        assertEquals("C", second.title());
        assertEquals(Long.valueOf(2), second.lockVersion());
        verify(requirements).save(second);
    }

    @Test
    void save_failure_does_not_publish_event_or_mutate_the_loaded_requirement() {
        IllegalStateException failure = new IllegalStateException("save failed");
        doThrow(failure).when(requirements).save(any());

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> service.changeTitle(L0, "B", Long.valueOf(0))));

        verify(requirements).save(any());
        verifyNoInteractions(publisher);
        assertEquals("A", before.title());
        assertEquals(Long.valueOf(0), before.lockVersion());
    }
}
