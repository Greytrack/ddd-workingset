package bootstrap;

import alm.requirement.application.command.RequirementCommandService;
import alm.requirement.application.command.RequirementSnapshot;
import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.domain.RequirementSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.*;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.event.SnapshotRefEvent;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSetRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证来源修改命令与仓储、快照和发布端口的协作。 */
class RequirementChangeFlowTest {

    private static final LocalId L0 = LocalId.of("260574482192320");
    private static final Revision R3 = Revision.of(3);
    private static final SnapshotRefEvent FIRST = SnapshotRefEvent.of("264246849363904");
    private static final SnapshotRefEvent SECOND = SnapshotRefEvent.of("258985471651776");

    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final RequirementSnapshotStore snapshots = mock(RequirementSnapshotStore.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final RequirementCommandService service = new RequirementCommandService(
            requirements, snapshots, new SnowflakeIdGenerator(1, 1), publisher,
            mock(sk.tracker.domain.repository.TrackerRepository.class),
            mock(WorkingSetRepository.class),
            mock(LightTrackerItemRepository.class));
    private final Requirement before = Requirement.create(
            L0, CommonId.of("265506033508288"), TrackerId.of("264905365135296"), "A", "source description", R3, Version.of("1.2"));

    @BeforeEach
    void setUp() {
        when(requirements.findById(L0)).thenReturn(Optional.of(before));
    }

    @Test
    void AT03_change_title_keeps_revision_and_version_but_increments_lock_version() {
        when(snapshots.save(any())).thenReturn(FIRST);

        Requirement changed = service.changeTitle(L0, "B", LockVersion.of(0));

        assertEquals("B", changed.title());
        assertEquals(R3, changed.revision());
        assertEquals(Version.of("1.2"), changed.version());
        assertEquals(LockVersion.of(1), changed.lockVersion());
        verify(requirements).save(changed);

        ArgumentCaptor<BusinessObjectUpdateEvent> events = ArgumentCaptor.forClass(BusinessObjectUpdateEvent.class);
        verify(publisher).publishEvent(events.capture());
        BusinessObjectUpdateEvent event = events.getValue();
        assertTrue(event.eventId() > 0);
        assertEquals(L0, event.requirementId());
        assertEquals(L0, event.sourceLocalId());
        assertEvent(event, FIRST, 0, 1);
        assertNotNull(event.occurredAt());

        ArgumentCaptor<RequirementSnapshot> captured = ArgumentCaptor.forClass(RequirementSnapshot.class);
        verify(snapshots).save(captured.capture());
        assertEquals("A", captured.getValue().title());
        assertEquals(R3, captured.getValue().revision());
        assertEquals(Version.of("1.2"), captured.getValue().version());
        assertEquals(LockVersion.of(0), captured.getValue().lockVersion());
    }

    @Test
    void event_is_published_after_the_repository_saves_the_changed_requirement() {
        when(snapshots.save(any())).thenReturn(FIRST);

        Requirement changed = service.changeTitle(L0, "B", LockVersion.of(0));

        org.mockito.InOrder order = inOrder(requirements, publisher);
        order.verify(requirements).save(changed);
        order.verify(publisher).publishEvent(any(BusinessObjectUpdateEvent.class));
    }

    @Test
    void stale_expected_lock_version_is_rejected_without_event_or_snapshot() {
        LockVersionConflictException conflict = assertThrows(LockVersionConflictException.class,
                () -> service.changeTitle(L0, "B", LockVersion.of(99)));

        assertEquals(LockVersion.of(99), conflict.expected());
        assertEquals(LockVersion.of(0), conflict.actual());
        verify(requirements, never()).save(any());
        verifyNoInteractions(snapshots, publisher);
    }

    @Test
    void consecutive_changes_publish_ordered_events_and_capture_each_pre_change_snapshot() {
        when(snapshots.save(any())).thenReturn(FIRST, SECOND);

        Requirement first = service.changeTitle(L0, "B", LockVersion.of(0));
        when(requirements.findById(L0)).thenReturn(Optional.of(first));
        Requirement second = service.changeTitle(L0, "C", LockVersion.of(1));

        ArgumentCaptor<RequirementSnapshot> captured = ArgumentCaptor.forClass(RequirementSnapshot.class);
        verify(snapshots, times(2)).save(captured.capture());
        assertEquals("A", captured.getAllValues().get(0).title());
        assertEquals("B", captured.getAllValues().get(1).title());
        assertEquals(LockVersion.of(0), captured.getAllValues().get(0).lockVersion());
        assertEquals(LockVersion.of(1), captured.getAllValues().get(1).lockVersion());

        ArgumentCaptor<BusinessObjectUpdateEvent> events = ArgumentCaptor.forClass(BusinessObjectUpdateEvent.class);
        verify(publisher, times(2)).publishEvent(events.capture());
        assertEvent(events.getAllValues().get(0), FIRST, 0, 1);
        assertEvent(events.getAllValues().get(1), SECOND, 1, 2);
        assertTrue(events.getAllValues().get(0).eventId() < events.getAllValues().get(1).eventId());
        assertEquals("C", second.title());
        assertEquals(LockVersion.of(2), second.lockVersion());
        verify(requirements).save(second);
    }

    @Test
    void save_failure_does_not_publish_event_or_mutate_the_loaded_requirement() {
        when(snapshots.save(any())).thenReturn(FIRST);
        IllegalStateException failure = new IllegalStateException("save failed");
        doThrow(failure).when(requirements).save(any());

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> service.changeTitle(L0, "B", LockVersion.of(0))));

        verify(requirements).save(any());
        verifyNoInteractions(publisher);
        assertEquals("A", before.title());
        assertEquals(LockVersion.of(0), before.lockVersion());
    }

    private static void assertEvent(BusinessObjectUpdateEvent event, SnapshotRefEvent ref, long beforeLock, long afterLock) {
        assertEquals(R3, event.beforeRevision());
        assertEquals(R3, event.afterRevision());
        assertEquals(LockVersion.of(beforeLock), event.beforeLockVersion());
        assertEquals(LockVersion.of(afterLock), event.afterLockVersion());
        assertEquals(ref, event.snapshotRef());
    }
}
