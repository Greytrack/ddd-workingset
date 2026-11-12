package sk.workingset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.application.port.LightTrackerItemRepository;

class CowEventHandlerTest {
    @Test
    void an_upgrade_shaped_event_matches_the_before_revision_and_keeps_the_targets_own_revision() {
        var items = mock(LightTrackerItemRepository.class);
        var failures = mock(CowFailureLog.class);
        var source = LocalId.of("source");
        var before = Revision.of(3);
        var after = Revision.of(4);
        var own = Revision.of(1);
        var eligible = target("eligible", source, own, before);
        var switched = LightTrackerItem.restore(eligible.localId(), eligible.commonId(), eligible.trackerId(),
                eligible.workingSetId(), own, eligible.localId(), own, true);
        when(items.findBySource(source, before)).thenReturn(List.of(eligible)).thenReturn(List.of(switched));
        when(items.findById(eligible.localId())).thenReturn(Optional.of(eligible));
        var event = new RequirementChanged(42, source, source, before, after,
                LockVersion.of(5), LockVersion.of(6), SnapshotRef.of("before-upgrade"), Instant.EPOCH);
        var store = mock(CowMaterializationStore.class);
        var pending = new CowMaterializationStore.Entry(42, eligible.localId(), source, before,
                CowMaterializationStore.Status.PENDING, 1, false, null);
        when(store.findByEventAndTarget(42, eligible.localId())).thenReturn(Optional.of(pending));
        when(store.lockByEventAndTarget(42, eligible.localId())).thenReturn(Optional.of(
                new CowMaterializationStore.Entry(42, eligible.localId(), source, before,
                        CowMaterializationStore.Status.MATERIALIZED, 1, true, null)));
        var writePort = mock(RequirementWritePort.class);
        doAnswer(call -> {
            call.getArgument(1, Runnable.class).run();
            return null;
        }).when(writePort).materialize(any(), any());
        var recovery = new CowRecoveryService(store, items, writePort, failures, Runnable::run, () -> {});
        var handler = new CowEventHandler(items, writePort, failures, Runnable::run, store, recovery);

        handler.onRequirementChanged(event);
        handler.onRequirementChanged(event);

        var request = ArgumentCaptor.forClass(MaterializeRequest.class);
        verify(writePort).materialize(request.capture(), any());
        assertEquals(before, request.getValue().beforeRevision());
        assertEquals(own, request.getValue().targetRevision());
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
        return LightTrackerItem.restore(LocalId.of(id), CommonId.of("family"), TrackerId.of("tracker"),
                WorkingSetId.of("ws"), own, source, sourceRevision, false);
    }
}
