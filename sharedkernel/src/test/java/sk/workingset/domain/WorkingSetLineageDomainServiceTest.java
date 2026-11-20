package sk.workingset.domain;

import org.junit.jupiter.api.Test;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSet.*;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetLineageDomainService;
import sk.workingset.error.BindingResCode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkingSetLineageDomainServiceTest {
    private final WorkingSet root = new WorkingSet(WorkingSet.DEFAULT_ID, null, List.of(), List.of());
    private final WorkingSetId childId = WorkingSetId.of("259712159403968");
    private final WorkingSetId grandchildId = WorkingSetId.of("265077320994752");

    private static void assertNotAdjacent(WorkingSet source, WorkingSet target) {
        DomainException failure = assertThrows(DomainException.class,
                () -> WorkingSetLineageDomainService.comparableBindings(source, target));
        assertEquals(BindingResCode.WORKING_SETS_NOT_ADJACENT, failure.getResCode());
    }

    @Test
    void allowsDirectParentChildInEitherDirectionAndExcludesSharedTrackers() {
        TrackerBinding shared = new TrackerBinding(childId, TrackerId.of("259191613022144"), "259944010602432", "258520000000031",
                SharingMode.SHARED, TrackerOrigin.SHARED_REUSE);
        TrackerBinding unshared = new TrackerBinding(childId, TrackerId.of("267052500774848"), "259944010602432", "258520000000032",
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
        Tracker parent = Tracker.original(TrackerId.of("260634912653248"), productSpace, classification, null);
        Tracker child = Tracker.derived(TrackerId.of("259712159403968"), parent, childId);
        Tracker sibling = Tracker.derived(TrackerId.of("263561533034432"), parent, childId);
        Tracker grandchild = Tracker.derived(TrackerId.of("265077320994752"), child, grandchildId);

        assertTrue(WorkingSetLineageDomainService.isDirectDerivation(child, parent));
        assertFalse(WorkingSetLineageDomainService.isDirectDerivation(grandchild, parent));
        assertFalse(WorkingSetLineageDomainService.isDirectDerivation(sibling, child));
    }

    @Test
    void rejectsSiblingsAndNonAdjacentAncestorsInEitherDirection() {
        WorkingSet child = WorkingSet.child(childId, root.id());
        WorkingSet sibling = WorkingSet.child(WorkingSetId.of("263561533034432"), root.id());
        WorkingSet grandchild = WorkingSet.child(grandchildId, child.id());

        assertNotAdjacent(child, sibling);
        assertNotAdjacent(root, grandchild);
        assertNotAdjacent(grandchild, root);
        assertNotAdjacent(child, child);
    }
}
