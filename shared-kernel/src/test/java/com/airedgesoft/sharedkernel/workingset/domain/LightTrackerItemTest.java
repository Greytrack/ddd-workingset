package com.airedgesoft.sharedkernel.workingset.domain;


import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LightTrackerItemTest {
    @Test
    void cancellingCheckoutRestoresFlagsAndAllowsRevisionReuse() {
        LightTrackerItem item = LightTrackerItem.local(L1, C1, T1, WS1, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);

        assertThrows(DomainException.class, () -> item.applyCheckoutCancellation(5, 4, "1000100001"));
        assertTrue(item.applyCheckout(3, 4));
        assertTrue(item.applyCheckoutCancellation(4, 3, "1000100001"));
        assertEquals(new LightTrackerItem.DowngradeIndexStatus(4, 3, 0, "1000100001", true, false, false),
                item.downgradeIndexStatus());
        assertEquals(R3, item.sourceRevision());
        assertEquals(List.of(R3), item.revisions().stream().map(member -> member.revision()).toList());

        assertFalse(item.applyCheckoutCancellation(4, 3, "1000100001"));
        assertTrue(item.applyCheckout(3, 4));
        assertEquals(Integer.valueOf(4), item.revision());
    }

    @Test
    void initialIndexStatusUsesTrackerClass() {
        LightTrackerItem requirement = LightTrackerItem.derive(
                L1, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, 99999L, 140101L, "test");
        LightTrackerItem other = LightTrackerItem.derive(
                L1, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, 99999L, 100701L, "test");

        assertEquals(new LightTrackerItem.InitialIndexStatus(0, "1110000000", false),
                requirement.initialIndexStatus());
        assertEquals(new LightTrackerItem.InitialIndexStatus(1, "1110100000", true),
                other.initialIndexStatus());
    }

    private static final long PRODUCT_SPACE_ID = 42L;
    private static final long CLASSIFICATION_ID = 14010101L;

    private static final Long L0 = Long.valueOf("260574482192320");
    private static final Long L1 = Long.valueOf("261158455371712");
    private static final Long C1 = Long.valueOf("265506033508288");
    private static final Long T1 = Long.valueOf("265625955250112");
    private static final Long WS1 = Long.valueOf("261508476721088");
    private static final Integer R3 = 3;

    @Test
    void derive_creates_source_backed_variant() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);

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
        LightTrackerItem item = LightTrackerItem.local(L1, C1, T1, WS1, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);
        assertFalse(item.deleted());
        assertTrue(item.delete());
        assertFalse(item.delete());
        assertTrue(item.deleted());
        assertEquals(R3, item.currentRevisionIndex().revision());
        assertEquals(L1, item.effectiveContentSource());
        assertEquals(R3, item.effectiveContentRevision());
        assertTrue(item.undelete());
        assertFalse(item.deleted());
    }

    @Test
    void derive_rejects_local_id_equal_to_source() {
        assertThrows(DomainException.class,
                () -> LightTrackerItem.derive(L0, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID));
    }

    @Test
    void switchToLocalBacked_updates_three_fields_and_keeps_identity() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);
        Long before = item.localId();

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
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);
        item.switchToLocalBacked(R3);

        assertThrows(DomainException.class, () -> item.switchToLocalBacked(R3));
    }

    @Test
    void switchToLocalBacked_rejects_revision_mismatch() {
        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);

        assertThrows(DomainException.class, () -> item.switchToLocalBacked(4));
    }

    @Test
    void explicitUpgradeRetainsPreviousRevisionAndIgnoresReplay() {
        LightTrackerItem item = LightTrackerItem.local(L1, C1, T1, WS1, R3, PRODUCT_SPACE_ID, CLASSIFICATION_ID);
        Integer r4 = 4;
        assertThrows(DomainException.class, () -> item.applyCheckout(r4, 5));
        assertTrue(item.applyCheckout(R3, r4));
        assertFalse(item.applyCheckout(R3, r4));
        assertEquals(new LightTrackerItem.EffectiveIndexStatus(
                r4, 1, "0010000000", false, false, true, false, false), item.upgradeIndexStatus());
        assertEquals(Integer.valueOf(0), item.currentRevisionIndex().flags());
        assertEquals(List.of(R3, r4), item.revisions().stream().map(member -> member.revision()).toList());
        assertEquals(r4, item.effectiveContentRevision());
    }

    @Test
    void restore_keeps_identity_and_state() {
        LightTrackerItem item = LightTrackerItem.restore(L1, C1, T1, WS1, R3, L0, R3, false, PRODUCT_SPACE_ID, CLASSIFICATION_ID);

        assertEquals(L1, item.localId());
        assertFalse(item.materialized());
        assertEquals(L0, item.effectiveContentSource());
    }

    @Test
    void derivedItemCarriesTrackerClassificationAndSpace() {
        long productSpaceId = 42L;
        long classificationId = 14010101L;

        LightTrackerItem item = LightTrackerItem.derive(L1, C1, T1, WS1, R3, L0, R3, productSpaceId, classificationId);

        assertEquals(42L, item.productSpaceId());
        assertEquals(14010101L, item.classificationId());
    }
}
