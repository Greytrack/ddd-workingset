package alm.requirement.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import alm.requirement.application.RequirementMergeWriter;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import org.junit.jupiter.api.Test;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.TargetIdentity;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

class RequirementMaterializationAdapterTest {
    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final SnapshotStore snapshots = mock(SnapshotStore.class);
    private final RequirementMaterializationAdapter adapter = new RequirementMaterializationAdapter(
            repository, snapshots, mock(RequirementMergeWriter.class));

    private final MaterializeRequest request = new MaterializeRequest(
            LocalId.of("target"), TrackerId.of("target-tracker"), CommonId.of("family"), LocalId.of("source"),
            Revision.of(3), Revision.of(4), LockVersion.of(1), LockVersion.of(2), Revision.of(1),
            10, new SnapshotRef("snapshot"));

    private final TargetIdentity target = new TargetIdentity(request.targetLocalId(), request.targetTrackerId(),
            request.commonId(), request.targetRevision(), false);

    @Test
    void refuses_missing_or_mismatched_snapshot_without_writing_target() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.empty());
        assertThrows(MaterializationRejectedException.class,
                () -> adapter.materialize(request, target));

        var invalid = java.util.List.of(
                snapshot("other", "family", 3, 1),
                snapshot("source", "family", 4, 1),
                snapshot("source", "family", 3, 2),
                snapshot("source", "other", 3, 1));
        for (var evidence : invalid) {
            when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(evidence));
            assertThrows(MaterializationRejectedException.class,
                    () -> adapter.materialize(request, target));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void target_identity_must_match_the_request_evidence() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(snapshot("source", "family", 3, 1)));
        var mismatched = new TargetIdentity(LocalId.of("other"), request.targetTrackerId(),
                request.commonId(), request.targetRevision(), false);
        assertThrows(MaterializationRejectedException.class,
                () -> adapter.materialize(request, mismatched));
        verifyNoInteractions(repository);
    }

    @Test
    void existing_target_must_match_evidence_including_release_version() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(snapshot("source", "family", 3, 1)));
        when(repository.findById(request.targetLocalId())).thenReturn(Optional.of(existing("different", "v1")),
                Optional.of(existing("A", "v2")), Optional.of(existing("A", "v1")));

        assertThrows(MaterializationRejectedException.class,
                () -> adapter.materialize(request, target));
        assertThrows(MaterializationRejectedException.class,
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
                TrackerId.of("source-tracker"), "A", "description", Revision.of(revision),
                Version.of("v1"), LockVersion.of(lockVersion)));
    }
}
