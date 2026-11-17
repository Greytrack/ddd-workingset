package sk.workingset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import sk.workingset.application.event.ReferenceEndpoint;
import sk.workingset.application.port.RelationSourcePort;
import sk.workingset.application.port.RelationSourcePort.ReferenceFact;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.MergeDecision;
import sk.workingset.domain.merge.MergeDecision.FieldAction;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeRound.Entry;
import sk.workingset.domain.merge.MergeRound.ItemResult;
import sk.workingset.domain.merge.MergeRound.Outcome;

class MergeRelationRepairStepTest {
    @Test
    void ignoresKeepTargetRejectedAndDeferredEntries() {
        var targetWorkingSet = WorkingSetId.of("259712159403968");
        var round = MergeRound.prepare("261365428617152", "request-2", WorkingSet.DEFAULT_ID, targetWorkingSet, "u")
                .register(entry("258520000000010", "265506033508288", "261158455371712", "265864557082560", new MergeDecision(Map.of("title", FieldAction.KEEP_TARGET),
                        null, false)))
                .register(entry("258520000000011", "258520000000013", "258520000000015", "258520000000016", new MergeDecision(Map.of("title", FieldAction.ACCEPT),
                        null, false)))
                .register(entry("258520000000012", "258520000000014", "258520000000017", "258520000000018", new MergeDecision(Map.of(), null, true)))
                .start();
        for (var entry : round.entries()) {
            round = round.finishEntry(entry, new ItemResult(entry.id(),
                    switch (entry.id()) {
                        case "258520000000010" -> Outcome.Applied;
                        case "258520000000011" -> Outcome.Rejected;
                        default -> Outcome.Deferred;
                    }, entry.decision().markAsMerged() ? "DEFER" : "KEEP_TARGET", null, null, null));
        }
        round = round.complete(Instant.now());

        var step = new MergeRelationRepairStep(mock(RelationSourcePort.class),
                new RelationEventPublisher(event -> { }, new AtomicLong(1)::incrementAndGet));
        assertEquals(0, step.changedTargets(round).size());
    }

    @Test
    void repairsOnlyTargetsChangedBySuccessfulMergeEntries() {
        var targetWorkingSet = WorkingSetId.of("259712159403968");
        var pair = new TrackerPair(WorkingSet.DEFAULT_ID, TrackerId.of("265625955250112"),
                targetWorkingSet, TrackerId.of("261538396602304"));
        var difference = new Difference(CommonId.of("265506033508288"), pair,
                side("261158455371712", Revision.of(3)), side("265864557082560", Revision.of(4)), List.of("title"));
        var decision = new MergeDecision(Map.of("title", FieldAction.ACCEPT), null, false);
        var round = MergeRound.prepare("265523938635712", "request-1", WorkingSet.DEFAULT_ID, targetWorkingSet, "u")
                .register(new Entry("263525235173312", 1, difference, decision, null)).start();
        var entry = round.entries().getFirst();
        round = round.finishEntry(entry, new ItemResult("263525235173312", Outcome.Applied, "ACCEPT", "265864557082560", null, null))
                .complete(Instant.now());

        RelationSourcePort source = mock(RelationSourcePort.class);
        var changedTarget = new ReferenceEndpoint(targetWorkingSet, LocalId.of("265864557082560"), CommonId.of("265506033508288"),
                TrackerId.of("261538396602304"), Revision.of(4));
        when(source.referencesTargeting(any())).thenReturn(List.of(new ReferenceFact("reference-1",
                new ReferenceEndpoint(WorkingSet.DEFAULT_ID, LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                        TrackerId.of("265625955250112"), Revision.of(3)), changedTarget)));

        var published = new ArrayList<Object>();
        var publisher = new RelationEventPublisher(published::add, new AtomicLong(10)::incrementAndGet);
        var step = new MergeRelationRepairStep(source, publisher);

        var result = step.repair(round);

        assertEquals(1, result.published());
        assertEquals(0, result.failed());
        assertEquals(1, published.size());
        assertEquals(1, step.changedTargets(round).size());
    }

    private static Entry entry(String id, String family, String sourceLocalId, String targetLocalId,
                               MergeDecision decision) {
        var pair = new TrackerPair(WorkingSet.DEFAULT_ID, TrackerId.of("265625955250112"),
                WorkingSetId.of("259712159403968"), TrackerId.of("261538396602304"));
        var difference = new Difference(CommonId.of(family), pair,
                side(sourceLocalId, Revision.of(3)), side(targetLocalId, Revision.of(4)), List.of("title"));
        return new Entry(id, 1, difference, decision, null);
    }

    private static Side side(String localId, Revision revision) {
        return new Side(LocalId.of(localId), LocalId.of(localId), revision, revision, "v", 0L,
                Presence.PRESENT, null, Map.of("title", "value"));
    }
}
