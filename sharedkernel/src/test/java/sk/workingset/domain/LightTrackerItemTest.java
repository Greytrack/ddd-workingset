package sk.workingset.domain;

import org.junit.jupiter.api.Test;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.lighttrackeritem.ItemClassification;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.workingset.WorkingSetId;

import static org.junit.jupiter.api.Assertions.*;

class LightTrackerItemTest {
    private static final ItemClassification CLASSIFICATION = new ItemClassification(42L, 14010101L);

    private static final LocalId L0 = LocalId.of("260574482192320");
    private static final LocalId L1 = LocalId.of("261158455371712");
    private static final CommonId C1 = CommonId.of("265506033508288");
    private static final TrackerId T1 = TrackerId.of("265625955250112");
    private static final WorkingSetId WS1 = WorkingSetId.of("261508476721088");
    private static final Revision R3 = Revision.of(3);

    @Test
    void derive_creates_source_backed_variant() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, CLASSIFICATION);

        assertEquals(L1, item.localId());
        assertEquals(C1, item.commonId());
        assertFalse(item.materialized());
        assertEquals(L0, item.sourceLocalId());
        assertEquals(R3, item.sourceRevision());
        assertEquals(R3, item.revision());
        // Source-backed 读取来源内容
        assertEquals(L0, item.effectiveContentSource());
        assertEquals(R3, item.effectiveContentRevision());
    }

    @Test
    void deletionChangesDoNotAlterRevisionOrContentLocation() {
        LightTrackerItem item = LightTrackerItem.local(L1, C1, T1, WS1, R3, CLASSIFICATION);
        assertFalse(item.deleted());
        assertTrue(item.recordDeletionState(true));
        assertFalse(item.recordDeletionState(true));
        assertTrue(item.deleted());
        assertEquals(R3, item.currentRevisionIndex().revision());
        assertEquals(L1, item.effectiveContentSource());
        assertEquals(R3, item.effectiveContentRevision());
        assertTrue(item.recordDeletionState(false));
        assertFalse(item.deleted());
    }

    @Test
    void derive_rejects_local_id_equal_to_source() {
        assertThrows(DomainException.class,
                () -> LightTrackerItem.derive(L0, C1, T1, WS1, R3, L0, R3, CLASSIFICATION));
    }

    @Test
    void switchToLocalBacked_updates_three_fields_and_keeps_identity() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, CLASSIFICATION);
        LocalId before = item.localId();

        item.switchToLocalBacked(R3);

        assertTrue(item.materialized());
        // AC-02：物化前后 LocalId 不变
        assertEquals(before, item.localId());
        // T3：三字段在同一次状态变更中一致
        assertEquals(item.localId(), item.sourceLocalId());
        assertEquals(item.revision(), item.sourceRevision());
        assertEquals(item.localId(), item.effectiveContentSource());
    }

    @Test
    void switchToLocalBacked_rejects_second_call() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, CLASSIFICATION);
        item.switchToLocalBacked(R3);

        assertThrows(DomainException.class, () -> item.switchToLocalBacked(R3));
    }

    @Test
    void switchToLocalBacked_rejects_revision_mismatch() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, CLASSIFICATION);

        assertThrows(DomainException.class, () -> item.switchToLocalBacked(Revision.of(4)));
    }

    @Test
    void explicitUpgradeRetainsPreviousRevisionAndIgnoresReplay() {
        LightTrackerItem item = LightTrackerItem.local(L1, C1, T1, WS1, R3, CLASSIFICATION);
        Revision r4 = Revision.of(4);
        assertThrows(DomainException.class, () -> item.advanceRevision(r4, Revision.of(5)));
        assertTrue(item.advanceRevision(R3, r4));
        assertFalse(item.advanceRevision(R3, r4));
        assertEquals(java.util.List.of(R3, r4), item.revisions().stream().map(member -> member.revision()).toList());
        assertEquals(r4, item.effectiveContentRevision());
    }

    @Test
    void restore_keeps_identity_and_state() {
        LightTrackerItem item = LightTrackerItem.restore(L1, C1, T1, WS1, R3, L0, R3, false, CLASSIFICATION);

        assertEquals(L1, item.localId());
        assertFalse(item.materialized());
        assertEquals(L0, item.effectiveContentSource());
    }

    @Test
    void derivedItemCarriesTrackerClassificationAndSpace() {
        ItemClassification classification = new ItemClassification(42L, 14010101L);

        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, classification);

        assertEquals(42L, item.numericProductSpaceId());
        assertEquals(14010101L, item.classificationId());
    }
}
