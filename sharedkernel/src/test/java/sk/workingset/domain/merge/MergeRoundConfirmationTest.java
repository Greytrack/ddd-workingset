package sk.workingset.domain.merge;

import org.junit.jupiter.api.Test;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.workingset.WorkingSetId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MergeRoundConfirmationTest {
    private static final WorkingSetId SOURCE = WorkingSetId.of("1");
    private static final WorkingSetId TARGET = WorkingSetId.of("20");
    private static final Revision BEFORE = Revision.of(3);
    private static final Revision COMMITTED = Revision.of(4);

    @Test
    void confirmedEntryOwnsCommittedRevisionEvidenceBeforeRoundCloses() {
        MergeRound running = round(true);
        MergeRound.Entry entry = running.entries().getFirst();
        assertThrows(DomainException.class, () -> running.finishEntry(entry,
                new MergeRound.ItemResult(entry.id(), MergeRound.Outcome.Applied, "UPDATE", null, null, null)));

        MergeRound finished = running.finishApplied(entry.id(), "UPDATE", receipt()).complete(Instant.now());
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
        assertEquals(MergeRound.Outcome.Applied, outcome.complete(Instant.now()).result().itemResults().getFirst().outcome());
    }

    private static MergeRound round(boolean markAsMerged) {
        var difference = new Difference(CommonId.of("10"), new TrackerPair(SOURCE, TrackerId.of("12"),
                TARGET, TrackerId.of("13")), side("30", "old"), side("31", "new"), List.of("title"));
        var decision = new MergeDecision(Map.of("title", MergeDecision.FieldAction.ACCEPT), null, markAsMerged);
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
}
