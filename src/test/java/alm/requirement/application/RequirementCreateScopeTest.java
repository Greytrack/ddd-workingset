package alm.requirement.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import alm.requirement.domain.RequirementRepository;
import alm.requirement.infrastructure.SnapshotStore;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.TrackerScopePort;
import sk.workingset.application.port.TrackerScopePort.TrackerScope;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** D10：新增工作集入口的归属解析（共享归默认、非共享归当前）。 */
class RequirementCreateScopeTest {

    private static final LocalId LOCAL = LocalId.of("L1");
    private static final CommonId COMMON = CommonId.of("C1");
    private static final TrackerId TRACKER = TrackerId.of("T1");
    private static final Revision REVISION = Revision.of(3);

    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final TrackerScopePort trackerScope = mock(TrackerScopePort.class);
    private final RequirementApplicationService service = new RequirementApplicationService(
            repository, mock(SnapshotStore.class), mock(IdentifierGenerator.class),
            mock(ApplicationEventPublisher.class), trackerScope);

    @Test
    void unshared_tracker_must_be_created_from_its_own_working_set() {
        when(trackerScope.find(TRACKER)).thenReturn(Optional.of(
                new TrackerScope(WorkingSetId.of("child"), SharingMode.UNSHARED)));

        var failure = assertThrows(ApplicationFailure.class,
                () -> service.create(LOCAL, COMMON, TRACKER, WorkingSetId.of("1"), "A", "d", REVISION));
        assertSame(FailureReason.NOT_INCLUDED, failure.reason());
        verify(repository, never()).save(any());
    }

    @Test
    void shared_tracker_can_be_created_from_any_working_set() {
        when(trackerScope.find(TRACKER)).thenReturn(Optional.of(
                new TrackerScope(WorkingSetId.of("1"), SharingMode.SHARED)));

        service.create(LOCAL, COMMON, TRACKER, WorkingSetId.of("child"), "A", "d", REVISION);

        verify(repository).save(any());
    }

    @Test
    void unknown_tracker_is_left_to_persistence_validation() {
        when(trackerScope.find(TRACKER)).thenReturn(Optional.empty());

        service.create(LOCAL, COMMON, TRACKER, WorkingSetId.of("1"), "A", "d", REVISION);

        verify(repository).save(any());
    }
}
