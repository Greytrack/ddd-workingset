package com.airedgesoft.alm.requirement.application;


import com.airedgesoft.alm.requirement.application.command.RequirementCommandService;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;
import com.airedgesoft.alm.requirement.domain.RequirementSnapshotStore;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T6-C：创建时的身份分配与工作集归属。
 */
class RequirementCreateScopeTest {

    private static final Long TRACKER = Long.valueOf("265625955250112");
    private static final Integer REVISION = 3;

    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final TrackerRepository trackers = mock(TrackerRepository.class);
    private final WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
    private final IdentifierGenerator ids = mock(IdentifierGenerator.class);
    private final RequirementCommandService service = new RequirementCommandService(
            repository, mock(RequirementSnapshotStore.class), ids, mock(ApplicationEventPublisher.class), trackers,
            workingSets, mock(LightTrackerItemRepository.class));

    private static Tracker tracker(String workingSetId, SharingMode sharingMode) {
        Tracker tracker = mock(Tracker.class);
        when(tracker.workingSetId()).thenReturn(Long.valueOf(workingSetId));
        when(tracker.sharingMode()).thenReturn(sharingMode);
        return tracker;
    }

    @BeforeEach
    void stubIdentifiers() {
        when(ids.nextId(null)).thenReturn(100L, 200L, 300L, 400L);
        Long root = WorkingSet.DEFAULT_ID;
        Long child = Long.valueOf("259712159403968");
        when(workingSets.findById(root)).thenReturn(Optional.of(new WorkingSet(root, null, List.of(), List.of())));
        when(workingSets.findById(child)).thenReturn(Optional.of(WorkingSet.child(child, root)));
    }

    @Test
    void default_working_set_uses_the_same_snowflake_for_both_ids() {
        Tracker tracker = tracker("1", SharingMode.UNSHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        Requirement created = service.create(TRACKER, Long.valueOf("1"), "A", "d", REVISION);

        assertEquals(Long.toString(created.localId()), Long.toString(created.commonId()));
    }

    @Test
    void non_default_working_set_uses_two_distinct_ids() {
        Tracker tracker = tracker("259712159403968", SharingMode.UNSHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        Requirement created = service.create(TRACKER, Long.valueOf("259712159403968"), "A", "d", REVISION);

        assertNotEquals(Long.toString(created.localId()), Long.toString(created.commonId()));
    }

    @Test
    void unshared_tracker_must_be_created_from_its_own_working_set() {
        Tracker tracker = tracker("259712159403968", SharingMode.UNSHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        DomainException failure = assertThrows(DomainException.class,
                () -> service.create(TRACKER, Long.valueOf("1"), "A", "d", REVISION));
        assertSame(FailureReason.NOT_INCLUDED, FailureReason.of(failure));
        verify(repository, never()).save(any());
    }

    @Test
    void shared_tracker_can_be_created_from_any_working_set() {
        Tracker tracker = tracker("1", SharingMode.SHARED);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));

        Requirement created = service.create(TRACKER, Long.valueOf("259712159403968"), "A", "d", REVISION);

        assertEquals(Long.toString(created.localId()), Long.toString(created.commonId()));
        verify(repository).save(any());
    }

    @Test
    void missing_requested_working_set_rejects_creation() {
        DomainException failure = assertThrows(DomainException.class,
                () -> service.create(TRACKER, Long.valueOf("42"), "A", "d", REVISION));

        assertSame(BindingResCode.WORKING_SET_NOT_EXISTS, failure.getResCode());
        verify(repository, never()).save(any());
    }

    @Test
    void unknown_tracker_rejects_creation_before_persistence() {
        when(trackers.findById(TRACKER)).thenReturn(Optional.empty());

        DomainException failure = assertThrows(DomainException.class,
                () -> service.create(TRACKER, WorkingSet.DEFAULT_ID, "A", "d", REVISION));
        assertSame(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT, failure.getResCode());
        verify(repository, never()).save(any());
    }
}
