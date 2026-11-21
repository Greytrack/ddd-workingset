package alm.requirement.application;

import alm.requirement.application.command.RequirementCommandService;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.domain.RequirementSnapshotStore;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.Revision;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T6-C：创建时的身份分配与工作集归属。
 */
class RequirementCreateScopeTest {

    private static final TrackerId TRACKER = TrackerId.of("265625955250112");
    private static final Revision REVISION = Revision.of(3);

    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final TrackerRepository trackers = mock(TrackerRepository.class);
    private final WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
    private final IdentifierGenerator ids = mock(IdentifierGenerator.class);
    private final RequirementCommandService service = new RequirementCommandService(
            repository, mock(RequirementSnapshotStore.class), ids, mock(ApplicationEventPublisher.class), trackers,
            workingSets, mock(LightTrackerItemRepository.class));

    private static Tracker tracker(String workingSetId, SharingMode sharingMode) {
        Tracker tracker = mock(Tracker.class);
        when(tracker.workingSetId()).thenReturn(WorkingSetId.of(workingSetId));
        when(tracker.sharingMode()).thenReturn(sharingMode);
        return tracker;
    }

    @BeforeEach
    void stubIdentifiers() {
        when(ids.nextId(null)).thenReturn(100L, 200L, 300L, 400L);
        WorkingSetId root = WorkingSet.DEFAULT_ID;
        WorkingSetId child = WorkingSetId.of("259712159403968");
        when(workingSets.findById(root)).thenReturn(Optional.of(new WorkingSet(root, null, List.of(), List.of())));
        when(workingSets.findById(child)).thenReturn(Optional.of(WorkingSet.child(child, root)));
    }

    @Test
    void default_working_set_uses_the_same_snowflake_for_both_ids() {
        Tracker tracker = tracker("1", SharingMode.UNSHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        Requirement created = service.create(TRACKER, WorkingSetId.of("1"), "A", "d", REVISION);

        assertEquals(created.localId().value(), created.commonId().value());
    }

    @Test
    void non_default_working_set_uses_two_distinct_ids() {
        Tracker tracker = tracker("259712159403968", SharingMode.UNSHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        Requirement created = service.create(TRACKER, WorkingSetId.of("259712159403968"), "A", "d", REVISION);

        assertNotEquals(created.localId().value(), created.commonId().value());
    }

    @Test
    void unshared_tracker_must_be_created_from_its_own_working_set() {
        Tracker tracker = tracker("259712159403968", SharingMode.UNSHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        DomainException failure = assertThrows(DomainException.class,
                () -> service.create(TRACKER, WorkingSetId.of("1"), "A", "d", REVISION));
        assertSame(FailureReason.NOT_INCLUDED, FailureReason.of(failure));
        verify(repository, never()).save(any());
    }

    @Test
    void shared_tracker_can_be_created_from_any_working_set() {
        Tracker tracker = tracker("1", SharingMode.SHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        Requirement created = service.create(TRACKER, WorkingSetId.of("259712159403968"), "A", "d", REVISION);

        assertEquals(created.localId().value(), created.commonId().value());
        verify(repository).save(any());
    }

    @Test
    void missing_requested_working_set_rejects_creation() {
        DomainException failure = assertThrows(DomainException.class,
                () -> service.create(TRACKER, WorkingSetId.of("42"), "A", "d", REVISION));

        assertSame(BindingResCode.WORKING_SET_NOT_EXISTS, failure.getResCode());
        verify(repository, never()).save(any());
    }

    @Test
    void unknown_tracker_is_left_to_persistence_validation() {
        when(trackers.findById(TRACKER)).thenReturn(Optional.empty());

        Requirement created = service.create(TRACKER, WorkingSetId.of("1"), "A", "d", REVISION);

        assertNotEquals(created.localId().value(), created.commonId().value());
        verify(repository).save(any());
    }
}
