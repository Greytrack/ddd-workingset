package com.airedgesoft.sharedkernel.workingset.application;


import com.airedgesoft.sharedkernel.workingset.application.command.MergeRelationRepairStep;
import com.airedgesoft.sharedkernel.workingset.application.command.RelationEventPublisher;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider.ReferenceFact;
import com.airedgesoft.sharedkernel.workingset.domain.event.ReferenceEndpointEvent;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Difference;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Presence;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Side;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.TrackerPair;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeDecision;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeDecision.FieldAction;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.Entry;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.ItemResult;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.Outcome;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MergeRelationRepairStepTest {
    private static Entry entry(String id, String family, String sourceLocalId, String targetLocalId,
                               MergeDecision decision) {
        TrackerPair pair = new TrackerPair(WorkingSet.DEFAULT_ID, Long.valueOf("265625955250112"),
                Long.valueOf("259712159403968"), Long.valueOf("261538396602304"));
        Difference difference = new Difference(Long.valueOf(family), pair,
                side(sourceLocalId, 3), side(targetLocalId, 4), List.of("title"));
        return new Entry(id, difference, decision, null);
    }

    private static Side side(String localId, Integer revision) {
        return new Side(Long.valueOf(localId), Long.valueOf(localId), revision, revision, "v", 0L,
                Presence.PRESENT, null, Map.of("title", "value"));
    }

    @Test
    void ignoresIgnoredAndRejectedEntries() {
        Long targetWorkingSet = Long.valueOf("259712159403968");
        MergeRound round = MergeRound.prepare("261365428617152", "request-2", WorkingSet.DEFAULT_ID, targetWorkingSet, "u")
                .register(entry("258520000000010", "265506033508288", "261158455371712", "265864557082560", new MergeDecision(Map.of("title", FieldAction.IGNORED),
                        null, false)))
                .register(entry("258520000000011", "258520000000013", "258520000000015", "258520000000016", new MergeDecision(Map.of("title", FieldAction.ACCEPT),
                        null, false)))
                .register(entry("258520000000012", "258520000000014", "258520000000017", "258520000000018", new MergeDecision(Map.of(), null, true)))
                .start();
        for (MergeRound.Entry entry : round.entries()) {
            round = round.finishEntry(entry, new ItemResult(entry.id(),
                    switch (entry.id()) {
                        case "258520000000010" -> Outcome.Applied;
                        case "258520000000011" -> Outcome.Rejected;
                        default -> Outcome.Rejected;
                    }, "IGNORED", null, null, null));
        }
        round = round.complete(System.currentTimeMillis());

        MergeRelationRepairStep step = new MergeRelationRepairStep(mock(RelationSourceQueryProvider.class),
                new RelationEventPublisher(event -> {
                }, entity -> 1L));
        assertEquals(0, step.changedTargets(round).size());
    }

    @Test
    void repairsOnlyTargetsChangedBySuccessfulMergeEntries() {
        Long targetWorkingSet = Long.valueOf("259712159403968");
        TrackerPair pair = new TrackerPair(WorkingSet.DEFAULT_ID, Long.valueOf("265625955250112"),
                targetWorkingSet, Long.valueOf("261538396602304"));
        Difference difference = new Difference(Long.valueOf("265506033508288"), pair,
                side("261158455371712", 3), side("265864557082560", 4), List.of("title"));
        MergeDecision decision = new MergeDecision(Map.of("title", FieldAction.ACCEPT), null, false);
        MergeRound round = MergeRound.prepare("265523938635712", "request-1", WorkingSet.DEFAULT_ID, targetWorkingSet, "u")
                .register(new Entry("263525235173312", difference, decision, null)).start();
        MergeRound.Entry entry = round.entries().getFirst();
        round = round.finishEntry(entry, new ItemResult("263525235173312", Outcome.Applied, "ACCEPT", "265864557082560", null, null))
                .complete(System.currentTimeMillis());

        RelationSourceQueryProvider source = mock(RelationSourceQueryProvider.class);
        ReferenceEndpointEvent changedTarget = new ReferenceEndpointEvent(targetWorkingSet, Long.valueOf("265864557082560"), Long.valueOf("265506033508288"),
                Long.valueOf("261538396602304"), 4);
        when(source.referencesTargeting(any())).thenReturn(List.of(new ReferenceFact("reference-1",
                new ReferenceEndpointEvent(WorkingSet.DEFAULT_ID, Long.valueOf("261158455371712"), Long.valueOf("265506033508288"),
                        Long.valueOf("265625955250112"), 3), changedTarget)));

        ArrayList<Object> published = new ArrayList<Object>();
        RelationEventPublisher publisher = new RelationEventPublisher(published::add, entity -> 10L);
        MergeRelationRepairStep step = new MergeRelationRepairStep(source, publisher);

        MergeRelationRepairStep.StepResult result = step.repair(round);

        assertEquals(1, result.published());
        assertEquals(0, result.failed());
        assertEquals(1, published.size());
        assertEquals(1, step.changedTargets(round).size());
    }
}
