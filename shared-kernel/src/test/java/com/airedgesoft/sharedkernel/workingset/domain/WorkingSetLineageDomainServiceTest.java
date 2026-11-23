package com.airedgesoft.sharedkernel.workingset.domain;


import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet.*;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetLineageDomainService;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkingSetLineageDomainServiceTest {
    private final WorkingSet root = new WorkingSet(WorkingSet.DEFAULT_ID, null, List.of(), List.of());
    private final Long childId = Long.valueOf("259712159403968");
    private final Long grandchildId = Long.valueOf("265077320994752");

    private static void assertNotAdjacent(WorkingSet source, WorkingSet target) {
        DomainException failure = assertThrows(DomainException.class,
                () -> WorkingSetLineageDomainService.comparableBindings(source, target));
        assertEquals(BindingResCode.WORKING_SETS_NOT_ADJACENT, failure.getResCode());
    }

    @Test
    void allowsDirectParentChildInEitherDirectionAndExcludesSharedTrackers() {
        TrackerBinding shared = new TrackerBinding(childId, Long.valueOf("259191613022144"), "259944010602432", "258520000000031",
                SharingMode.SHARED, TrackerOrigin.SHARED_REUSE);
        TrackerBinding unshared = new TrackerBinding(childId, Long.valueOf("267052500774848"), "259944010602432", "258520000000032",
                SharingMode.UNSHARED, TrackerOrigin.UNSHARED_DERIVATION);
        SpaceBinding space = new SpaceBinding(childId, "259944010602432", SpaceOrigin.EXPLICIT, SpaceState.ACTIVE);
        WorkingSet child = new WorkingSet(childId, root.id(), List.of(space), List.of(shared, unshared));
        WorkingSet grandchild = WorkingSet.child(grandchildId, child.id());

        assertEquals(List.of(unshared), WorkingSetLineageDomainService.comparableBindings(root, child));
        assertEquals(List.of(unshared), WorkingSetLineageDomainService.comparableBindings(child, root));
        assertTrue(WorkingSetLineageDomainService.comparableBindings(child, grandchild).isEmpty());
    }

    @Test
    void directDerivationRequiresChildSourceIdToBeTheImmediateParent() {
        ProductSpace productSpace = new ProductSpace("259944010602432");
        Classification classification = new Classification(266673196957632L, SharingMode.UNSHARED,
                "测试分类", 11L, 111L);
        Tracker parent = Tracker.original(Long.valueOf("260634912653248"), productSpace, classification, null);
        Tracker child = Tracker.derived(Long.valueOf("259712159403968"), parent, childId);
        Tracker sibling = Tracker.derived(Long.valueOf("263561533034432"), parent, childId);
        Tracker grandchild = Tracker.derived(Long.valueOf("265077320994752"), child, grandchildId);

        assertTrue(WorkingSetLineageDomainService.isDirectDerivation(child, parent));
        assertFalse(WorkingSetLineageDomainService.isDirectDerivation(grandchild, parent));
        assertFalse(WorkingSetLineageDomainService.isDirectDerivation(sibling, child));
    }

    @Test
    void rejectsSiblingsAndNonAdjacentAncestorsInEitherDirection() {
        WorkingSet child = WorkingSet.child(childId, root.id());
        WorkingSet sibling = WorkingSet.child(Long.valueOf("263561533034432"), root.id());
        WorkingSet grandchild = WorkingSet.child(grandchildId, child.id());

        assertNotAdjacent(child, sibling);
        assertNotAdjacent(root, grandchild);
        assertNotAdjacent(grandchild, root);
        assertNotAdjacent(child, child);
    }
}
