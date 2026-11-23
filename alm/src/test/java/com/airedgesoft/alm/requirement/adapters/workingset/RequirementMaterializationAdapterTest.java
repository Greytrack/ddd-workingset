package com.airedgesoft.alm.requirement.adapters.workingset;


import com.airedgesoft.alm.requirement.application.command.RequirementMaterializationService;
import com.airedgesoft.alm.requirement.application.command.RequirementSnapshot;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;
import com.airedgesoft.alm.requirement.domain.RequirementSnapshotStore;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.application.command.dto.MaterializeRequest;
import com.airedgesoft.sharedkernel.workingset.application.common.TargetIdentity;
import com.airedgesoft.sharedkernel.workingset.domain.event.SnapshotRefEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RequirementMaterializationAdapterTest {
    private final RequirementRepository repository = mock(RequirementRepository.class);
    private final RequirementSnapshotStore snapshots = mock(RequirementSnapshotStore.class);
    private final RequirementMaterializationService adapter = new RequirementMaterializationService(
            repository, snapshots);

    private final MaterializeRequest request = new MaterializeRequest(
            Long.valueOf("260939964213184"), Long.valueOf("263244057497536"), Long.valueOf("264220103598016"), Long.valueOf("261802623213504"),
            3, 4, Long.valueOf(1), Long.valueOf(2), 1,
            10, SnapshotRefEvent.of("258519900000001"));

    private final TargetIdentity target = new TargetIdentity(request.targetLocalId(), request.targetTrackerId(),
            request.commonId(), request.targetRevision(), false);

    @Test
    void refuses_missing_or_mismatched_snapshot_without_writing_target() {
        when(snapshots.find(request.snapshotRef())).thenReturn(Optional.empty());
        assertThrows(DomainException.class,
                () -> adapter.materialize(request, target));

        List<RequirementSnapshot> invalid = List.of(
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
        TargetIdentity mismatched = new TargetIdentity(Long.valueOf("265987761440704"), request.targetTrackerId(),
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
                title, "description", request.targetRevision(), version);
    }

    private RequirementSnapshot snapshot(String source, String family, int revision, long lockVersion) {
        return RequirementSnapshot.of(Requirement.restore(Long.valueOf(source), Long.valueOf(family),
                Long.valueOf("258924796270528"), "A", "description", revision,
                "v1", lockVersion));
    }
}
