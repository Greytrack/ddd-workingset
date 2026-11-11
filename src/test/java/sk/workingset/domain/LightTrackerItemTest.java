package sk.workingset.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightTrackerItemTest {

    private static final LocalId L0 = LocalId.of("L0");
    private static final LocalId L1 = LocalId.of("L1");
    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T1 = TrackerId.of("T1");
    private static final WorkingSetId WS1 = WorkingSetId.of("WS1");
    private static final Revision R3 = Revision.of(3);

    @Test
    void derive_creates_source_backed_variant() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0);

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
    void derive_rejects_local_id_equal_to_source() {
        assertThrows(IllegalArgumentException.class,
                () -> LightTrackerItem.derive(L0, C1, T1, WS1, R3, L0));
    }

    @Test
    void switchToLocalBacked_updates_three_fields_and_keeps_identity() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0);
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
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0);
        item.switchToLocalBacked(R3);

        assertThrows(IllegalStateException.class, () -> item.switchToLocalBacked(R3));
    }

    @Test
    void switchToLocalBacked_rejects_revision_mismatch() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0);

        assertThrows(IllegalArgumentException.class, () -> item.switchToLocalBacked(Revision.of(4)));
    }

    @Test
    void restore_keeps_identity_and_state() {
        LightTrackerItem item = LightTrackerItem.restore(L1, C1, T1, WS1, R3, L0, R3, false);

        assertEquals(L1, item.localId());
        assertFalse(item.materialized());
        assertEquals(L0, item.effectiveContentSource());
    }
}
