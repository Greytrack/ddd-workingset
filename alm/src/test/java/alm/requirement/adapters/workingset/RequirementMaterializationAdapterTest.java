package alm.requirement.adapters.workingset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import alm.requirement.application.command.RequirementSnapshot;
import alm.requirement.application.command.RequirementMaterializationService;
import alm.requirement.domain.SnapshotRepository;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import org.junit.jupiter.api.Test;
import sk.workingset.domain.event.SnapshotRefEvent;
import sk.common.error.DomainException;
import sk.workingset.application.command.dto.MaterializeRequest;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.Version;

class RequirementMaterializationAdapterTest {
    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final SnapshotRepository snapshots = mock(SnapshotRepository.class);
    private final RequirementMaterializationService adapter = new RequirementMaterializationService(
            repository, snapshots);

    private final MaterializeRequest request = new MaterializeRequest(
            LocalId.of("260939964213184"), TrackerId.of("263244057497536"), CommonId.of("264220103598016"), LocalId.of("261802623213504"),
            Revision.of(3), Revision.of(4), LockVersion.of(1), LockVersion.of(2), Revision.of(1),
            10, SnapshotRefEvent.of("258519900000001"));

    private final TargetIdentity target = new TargetIdentity(request.targetLocalId(), request.targetTrackerId(),
            request.commonId(), request.targetRevision(), false);

    @Test
    void refuses_missing_or_mismatched_snapshot_without_writing_target() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.empty());
        assertThrows(DomainException.class,
                () -> adapter.materialize(request, target));

        java.util.List<RequirementSnapshot> invalid = java.util.List.of(
                snapshot("265987761440704", "264220103598016", 3, 1),
                snapshot("261802623213504", "264220103598016", 4, 1),
                snapshot("261802623213504", "264220103598016", 3, 2),
                snapshot("261802623213504", "265987761440704", 3, 1));
        for (RequirementSnapshot evidence : invalid) {
            when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(evidence));
            assertThrows(DomainException.class,
                    () -> adapter.materialize(request, target));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void target_identity_must_match_the_request_evidence() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(snapshot("261802623213504", "264220103598016", 3, 1)));
        TargetIdentity mismatched = new TargetIdentity(LocalId.of("265987761440704"), request.targetTrackerId(),
                request.commonId(), request.targetRevision(), false);
        assertThrows(DomainException.class,
                () -> adapter.materialize(request, mismatched));
        verifyNoInteractions(repository);
    }

    @Test
    void existing_target_must_match_evidence_including_release_version() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(snapshot("261802623213504", "264220103598016", 3, 1)));
        when(repository.findById(request.targetLocalId())).thenReturn(Optional.of(existing("different", "v1")),
                Optional.of(existing("A", "v2")), Optional.of(existing("A", "v1")));

        assertThrows(DomainException.class,
                () -> adapter.materialize(request, target));
        assertThrows(DomainException.class,
                () -> adapter.materialize(request, target));
        assertDoesNotThrow(() -> adapter.materialize(request, target));
        verify(repository, never()).save(any());
    }

    private Requirement existing(String title, String version) {
        return Requirement.create(request.targetLocalId(), request.commonId(), request.targetTrackerId(),
                title, "description", request.targetRevision(), Version.of(version));
    }

    private RequirementSnapshot snapshot(String source, String family, int revision, long lockVersion) {
        return RequirementSnapshot.of(Requirement.restore(LocalId.of(source), CommonId.of(family),
                TrackerId.of("258924796270528"), "A", "description", Revision.of(revision),
                Version.of("v1"), LockVersion.of(lockVersion)));
    }
}
