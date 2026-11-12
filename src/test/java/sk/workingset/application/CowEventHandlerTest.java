package sk.workingset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.MaterializeRequest;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.infrastructure.InMemoryLightTrackerItemRepository;

class CowEventHandlerTest {
    @Test
    void an_upgrade_shaped_event_matches_the_before_revision_and_keeps_the_targets_own_revision() {
        var items = new InMemoryLightTrackerItemRepository();
        var requests = new ArrayList<MaterializeRequest>();
        var failures = new CowFailureLog() {
            @Override
            public void record(Failure failure) {
                throw new AssertionError(failure);
            }

            @Override
            public List<Failure> findBySource(LocalId sourceLocalId) {
                return List.of();
            }
        };
        var handler = new CowEventHandler(items, requests::add, failures, Runnable::run);
        var source = LocalId.of("source");
        var before = Revision.of(3);
        var after = Revision.of(4);
        var own = Revision.of(1);
        var eligible = target("eligible", source, own, before);
        var ineligible = target("ineligible", source, own, after);
        items.save(eligible);
        items.save(ineligible);
        var event = new RequirementChanged(42, source, source, before, after,
                LockVersion.of(5), LockVersion.of(6), SnapshotRef.of("before-upgrade"), Instant.EPOCH);

        handler.onRequirementChanged(event);
        handler.onRequirementChanged(event);

        assertEquals(1, requests.size());
        assertEquals(before, requests.getFirst().beforeRevision());
        assertEquals(own, requests.getFirst().targetRevision());
        var materialized = items.findById(eligible.localId()).orElseThrow();
        assertTrue(materialized.materialized());
        assertEquals(own, materialized.sourceRevision());
        assertFalse(items.findById(ineligible.localId()).orElseThrow().materialized());
    }

    private static LightTrackerItem target(String id, LocalId source, Revision own, Revision sourceRevision) {
        return LightTrackerItem.restore(LocalId.of(id), CommonId.of("family"), TrackerId.of("tracker"),
                WorkingSetId.of("ws"), own, source, sourceRevision, false);
    }
}
