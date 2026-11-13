package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.infrastructure.RequirementSnapshot;
import alm.requirement.infrastructure.SnapshotStore;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;
import sk.workingset.infrastructure.SnowflakeIdGenerator;

/** 验证来源修改命令与仓储、快照和发布端口的协作。 */
class RequirementChangeFlowTest {

    private static final LocalId L0 = LocalId.of("L0");
    private static final Revision R3 = Revision.of(3);
    private static final SnapshotRef FIRST = SnapshotRef.of("before-first-edit");
    private static final SnapshotRef SECOND = SnapshotRef.of("before-second-edit");

    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final SnapshotStore snapshots = mock(SnapshotStore.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final RequirementApplicationService service = new RequirementApplicationService(
            requirements, snapshots, new SnowflakeIdGenerator(1, 1), publisher,
            mock(sk.workingset.application.port.TrackerScopePort.class));
    private final Requirement before = Requirement.create(
            L0, CommonId.of("C1"), TrackerId.of("T0"), "A", "source description", R3, Version.of("1.2"));

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

        var events = ArgumentCaptor.forClass(RequirementChanged.class);
        verify(publisher).publishEvent(events.capture());
        var event = events.getValue();
        assertTrue(event.eventId() > 0);
        assertEquals(L0, event.requirementId());
        assertEquals(L0, event.sourceLocalId());
        assertEvent(event, FIRST, 0, 1);
        assertNotNull(event.occurredAt());

        var captured = ArgumentCaptor.forClass(RequirementSnapshot.class);
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

        var order = inOrder(requirements, publisher);
        order.verify(requirements).save(changed);
        order.verify(publisher).publishEvent(any(RequirementChanged.class));
    }

    @Test
    void stale_expected_lock_version_is_rejected_without_event_or_snapshot() {
        var conflict = assertThrows(LockVersionConflictException.class,
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

        var captured = ArgumentCaptor.forClass(RequirementSnapshot.class);
        verify(snapshots, times(2)).save(captured.capture());
        assertEquals("A", captured.getAllValues().get(0).title());
        assertEquals("B", captured.getAllValues().get(1).title());
        assertEquals(LockVersion.of(0), captured.getAllValues().get(0).lockVersion());
        assertEquals(LockVersion.of(1), captured.getAllValues().get(1).lockVersion());

        var events = ArgumentCaptor.forClass(RequirementChanged.class);
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
        var failure = new IllegalStateException("save failed");
        doThrow(failure).when(requirements).save(any());

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> service.changeTitle(L0, "B", LockVersion.of(0))));

        verify(requirements).save(any());
        verifyNoInteractions(publisher);
        assertEquals("A", before.title());
        assertEquals(LockVersion.of(0), before.lockVersion());
    }

    private static void assertEvent(RequirementChanged event, SnapshotRef ref, long beforeLock, long afterLock) {
        assertEquals(R3, event.beforeRevision());
        assertEquals(R3, event.afterRevision());
        assertEquals(LockVersion.of(beforeLock), event.beforeLockVersion());
        assertEquals(LockVersion.of(afterLock), event.afterLockVersion());
        assertEquals(ref, event.snapshotRef());
    }
}
