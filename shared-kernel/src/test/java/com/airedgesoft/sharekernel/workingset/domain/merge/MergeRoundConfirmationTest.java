package com.airedgesoft.sharekernel.workingset.domain.merge;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.Difference;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.Presence;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.Side;
import com.airedgesoft.sharekernel.workingset.domain.merge.Comparison.TrackerPair;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MergeRoundConfirmationTest {
    private static final WorkingSetId SOURCE = WorkingSetId.of("1");
    private static final WorkingSetId TARGET = WorkingSetId.of("20");
    private static final Revision BEFORE = Revision.of(3);
    private static final Revision COMMITTED = Revision.of(4);

    private static MergeRound round(boolean markAsMerged) {
        Difference difference = new Difference(CommonId.of("10"), new TrackerPair(SOURCE, TrackerId.of("12"),
                TARGET, TrackerId.of("13")), side("30", "old"), side("31", "new"), List.of("title"));
        MergeDecision decision = new MergeDecision(Map.of("title", MergeDecision.FieldAction.ACCEPT), null, markAsMerged);
        return MergeRound.prepare("40", "request", SOURCE, TARGET, "user")
                .register(new MergeRound.Entry("41", difference, decision, null)).start();
    }

    private static Side side(String localId, String title) {
        LocalId id = LocalId.of(localId);
        return new Side(id, id, BEFORE, BEFORE, null, 0L, Presence.PRESENT, null, Map.of("title", title));
    }

    private static MergeCommitReceipt receipt() {
        return new MergeCommitReceipt(LocalId.of("31"), BEFORE, COMMITTED);
    }

    @Test
    void confirmedEntryOwnsCommittedRevisionEvidenceBeforeRoundCloses() {
        MergeRound running = round(true);
        MergeRound.Entry entry = running.entries().getFirst();
        assertThrows(DomainException.class, () -> running.finishEntry(entry,
                new MergeRound.ItemResult(entry.id(), MergeRound.Outcome.Applied, "UPDATE", null, null, null)));

        MergeRound finished = running.finishApplied(entry.id(), "UPDATE", receipt()).complete(System.currentTimeMillis());
        assertEquals(MergeRound.State.Succeeded, finished.state());
        assertEquals(new MergeRound.Confirmation(BEFORE, COMMITTED), finished.entries().getFirst().confirmation());
        assertEquals(MergeRound.Outcome.Confirmed, finished.result().itemResults().getFirst().outcome());
        assertThrows(DomainException.class, () -> finished.finishApplied(entry.id(), "UPDATE", receipt()));
    }

    @Test
    void successfulEntryWithoutMarkAsMergedHasNoConfirmation() {
        MergeRound running = round(false);
        MergeRound outcome = running.finishApplied(running.entries().getFirst().id(), "UPDATE", receipt());
        assertNull(outcome.entries().getFirst().confirmation());
        assertEquals(MergeRound.Outcome.Applied, outcome.complete(System.currentTimeMillis()).result().itemResults().getFirst().outcome());
    }

    @Test
    void onlyAcceptedAdditionConfirmsAutomatically() {
        TrackerPair pair = new TrackerPair(SOURCE, TrackerId.of("12"), TARGET, TrackerId.of("13"));
        Side present = side("30", "source");
        Side deleted = new Side(LocalId.of("31"), LocalId.of("31"), BEFORE, BEFORE,
                null, 2L, Presence.DELETED, null, Map.of());
        Difference addition = new Difference(CommonId.of("10"), pair, present, deleted, List.of());
        Difference deletion = new Difference(CommonId.of("10"), pair, deleted, present, List.of());
        MergeDecision accept = new MergeDecision(Map.of(), MergeDecision.ExistenceAction.ACCEPT, false);
        MergeDecision ignore = new MergeDecision(Map.of(), MergeDecision.ExistenceAction.IGNORED, false);
        assertEquals(Comparison.ChangeKind.ADDED, addition.kind());
        accept.validate(addition);
        ignore.validate(addition);
        accept.validate(deletion);
        assertTrue(accept.confirmsOnSuccess(addition));
        assertFalse(ignore.confirmsOnSuccess(addition));
        assertFalse(accept.confirmsOnSuccess(deletion));
        assertThrows(DomainException.class, () ->
                new MergeDecision(Map.of(), MergeDecision.ExistenceAction.ACCEPT, true).validate(addition));
    }

    @Test
    void missingFieldIsNotAnImplicitIgnore() {
        Difference existing = round(false).entries().getFirst().evidence();
        Difference twoFields = new Difference(existing.family(), existing.trackers(), existing.source(),
                existing.target(), List.of("title", "description"));
        assertThrows(DomainException.class, () ->
                new MergeDecision(Map.of("title", MergeDecision.FieldAction.ACCEPT), null, false).validate(twoFields));
        assertDoesNotThrow(() -> new MergeDecision(Map.of("title", MergeDecision.FieldAction.ACCEPT,
                "description", MergeDecision.FieldAction.IGNORED), null, false).validate(twoFields));
    }
}
