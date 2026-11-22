package com.airedgesoft.sharekernel.tracker.domain;

import com.airedgesoft.sharekernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharekernel.workingset.domain.Classification;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerTest {
    @Test
    void descendantsKeepFamilyIdentityAndLinkToTheirImmediateSource() {
        Classification classification = new Classification(301L, Classification.SharingMode.UNSHARED, "分类",
                11L, 111L);
        Tracker root = Tracker.original(TrackerId.of(101), new ProductSpace("201"),
                classification, null);
        Tracker child = Tracker.derived(TrackerId.of(102), root, WorkingSetId.of(2L));
        Tracker grandchild = Tracker.derived(TrackerId.of(103), child, WorkingSetId.of(3L));

        assertEquals(root.trackerId(), root.commonId());
        assertEquals(root.commonId(), child.commonId());
        assertEquals(root.commonId(), grandchild.commonId());
        assertEquals(child.trackerId(), grandchild.sourceId());
        assertEquals(301L, grandchild.numericClassificationId());
        assertEquals(11L, grandchild.baseClassId());
        assertEquals(111L, grandchild.classId());
        assertEquals(TrackerId.of(103), grandchild.compatibility().trackerId());
        assertEquals(301L, grandchild.compatibility().originalClassificationId());
    }
}
