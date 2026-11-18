package sk.workingset.application;

import sk.workingset.application.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;
import sk.workingset.application.command.CowEventHandler;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.event.SnapshotRef;
import sk.workingset.application.command.dto.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.repository.CowMaterializationRepository;
import sk.workingset.domain.repository.LightTrackerItemRepository;

class CowEventHandlerTest {
    @Test
    void an_upgrade_shaped_event_matches_the_before_revision_and_keeps_the_targets_own_revision() {
        var items = mock(LightTrackerItemRepository.class);
        var failures = mock(CowFailureLog.class);
        var source = LocalId.of("261802623213504");
        var before = Revision.of(3);
        var after = Revision.of(4);
        var own = Revision.of(1);
        var eligible = target("258520000000001", source, own, before);
        var switched = LightTrackerItem.restore(eligible.localId(), eligible.commonId(), eligible.trackerId(),
                eligible.workingSetId(), own, eligible.localId(), own, true);
        when(items.findBySource(source, before)).thenReturn(List.of(eligible)).thenReturn(List.of(switched));
        when(items.findById(eligible.localId())).thenReturn(Optional.of(eligible));
        var event = new BusinessObjectUpdateEvent(42, source, source, before, after,
                LockVersion.of(5), LockVersion.of(6), SnapshotRef.of("258782756974528"), Instant.EPOCH);
        var store = mock(CowMaterializationRepository.class);
        var pending = new CowMaterializationRepository.Entry(42, eligible.localId(), source, before,
                CowMaterializationRepository.Status.PENDING, 1, false, null);
        when(store.findByEventAndTarget(42, eligible.localId())).thenReturn(Optional.of(pending));
        when(store.lockByEventAndTarget(42, eligible.localId())).thenReturn(Optional.of(
                new CowMaterializationRepository.Entry(42, eligible.localId(), source, before,
                        CowMaterializationRepository.Status.MATERIALIZED, 1, true, null)));
        var lifecycle = mock(TrackerItemLifecyclePort.class);
        when(lifecycle.lockMaterializationTarget(eligible.trackerId(), eligible.localId()))
                .thenReturn(TargetIdentity.of(eligible));
        var writePort = mock(RequirementWritePort.class);
        var transaction = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            ((java.util.function.Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        var recovery = new CowRecoveryService(store, items, writePort, failures, transaction, () -> {});
        var handler = new CowEventHandler(items, lifecycle, writePort, failures, transaction, store, recovery);

        handler.onTrackerItemChanged(event);
        handler.onTrackerItemChanged(event);

        var request = ArgumentCaptor.forClass(MaterializeRequest.class);
        var identity = ArgumentCaptor.forClass(TargetIdentity.class);
        verify(writePort).materialize(request.capture(), identity.capture());
        assertEquals(before, request.getValue().beforeRevision());
        assertEquals(own, request.getValue().targetRevision());
        assertEquals(eligible.localId(), identity.getValue().localId());
        verify(lifecycle).lockMaterializationTarget(eligible.trackerId(), eligible.localId());
        verify(store).markMaterialized(42, eligible.localId());
        var saved = ArgumentCaptor.forClass(LightTrackerItem.class);
        verify(items).save(saved.capture());
        var materialized = saved.getValue();
        assertTrue(materialized.materialized());
        assertEquals(eligible.localId(), materialized.localId());
        assertEquals(eligible.localId(), materialized.sourceLocalId());
        assertEquals(own, materialized.revision());
        assertEquals(own, materialized.sourceRevision());
    }

    private static LightTrackerItem target(String id, LocalId source, Revision own, Revision sourceRevision) {
        return LightTrackerItem.restore(LocalId.of(id), CommonId.of("264220103598016"), TrackerId.of("264443044134848"),
                WorkingSetId.of("266514385749952"), own, source, sourceRevision, false);
    }
}
