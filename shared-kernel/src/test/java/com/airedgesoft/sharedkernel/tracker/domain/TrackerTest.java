package com.airedgesoft.sharedkernel.tracker.domain;


import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerTest {
    @Test
    void descendantsKeepFamilyIdentityAndLinkToTheirImmediateSource() {
        ClassSnapshot classification = ClassSnapshot.build(301L, false, "分类",
                11L, 111L);
        Tracker root = Tracker.original(Long.valueOf(101), new ProductSpace("201"),
                classification, null);
        Tracker child = Tracker.derived(Long.valueOf(102), root, 2L);
        Tracker grandchild = Tracker.derived(Long.valueOf(103), child, 3L);

        assertEquals(root.trackerId(), root.commonId());
        assertEquals(root.commonId(), child.commonId());
        assertEquals(root.commonId(), grandchild.commonId());
        assertEquals(child.trackerId(), grandchild.sourceId());
        assertEquals(301L, grandchild.numericClassificationId());
        assertEquals(11L, grandchild.baseClassId());
        assertEquals(111L, grandchild.classId());
    }
}
