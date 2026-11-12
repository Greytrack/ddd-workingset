package alm.requirement.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.function.Consumer;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

class RequirementMaterializationAdapterTest {
    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final SnapshotStore snapshots = mock(SnapshotStore.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final RequirementMaterializationAdapter adapter =
            new RequirementMaterializationAdapter(repository, snapshots, transaction);

    private final MaterializeRequest request = new MaterializeRequest(
            LocalId.of("target"), TrackerId.of("target-tracker"), CommonId.of("family"), LocalId.of("source"),
            Revision.of(3), Revision.of(4), LockVersion.of(1), LockVersion.of(2), Revision.of(1),
            10, new SnapshotRef("snapshot"));

    @Test
    void refuses_missing_or_mismatched_snapshot_without_writing_target() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.empty());
        assertThrows(MaterializationRejectedException.class, () -> adapter.materialize(request));

        var invalid = java.util.List.of(
                snapshot("other", "family", 3, 1),
                snapshot("source", "family", 4, 1),
                snapshot("source", "family", 3, 2),
                snapshot("source", "other", 3, 1));
        for (var evidence : invalid) {
            when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(evidence));
            assertThrows(MaterializationRejectedException.class, () -> adapter.materialize(request));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void existing_target_must_match_evidence_including_release_version() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.of(snapshot("source", "family", 3, 1)));
        doAnswer(call -> {
            Consumer<TransactionStatus> work = call.getArgument(0);
            work.accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(repository.findById(request.targetLocalId())).thenReturn(Optional.of(target("different", "v1")),
                Optional.of(target("A", "v2")), Optional.of(target("A", "v1")));

        assertThrows(MaterializationRejectedException.class, () -> adapter.materialize(request));
        assertThrows(MaterializationRejectedException.class, () -> adapter.materialize(request));
        assertDoesNotThrow(() -> adapter.materialize(request));
        verify(repository, never()).save(any());
    }

    private Requirement target(String title, String version) {
        return Requirement.create(request.targetLocalId(), request.commonId(), request.targetTrackerId(),
                title, "description", request.targetRevision(), Version.of(version));
    }

    private RequirementSnapshot snapshot(String source, String family, int revision, long lockVersion) {
        return RequirementSnapshot.of(Requirement.restore(LocalId.of(source), CommonId.of(family),
                TrackerId.of("source-tracker"), "A", "description", Revision.of(revision),
                Version.of("v1"), LockVersion.of(lockVersion)));
    }
}
