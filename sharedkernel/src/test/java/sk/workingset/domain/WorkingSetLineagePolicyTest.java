package sk.workingset.domain;

import org.junit.jupiter.api.Test;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSet.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkingSetLineagePolicyTest {
    private final WorkingSet root = new WorkingSet(WorkingSet.DEFAULT_ID, null, List.of(), List.of());
    private final WorkingSetId childId = WorkingSetId.of("259712159403968");
    private final WorkingSetId grandchildId = WorkingSetId.of("265077320994752");

    private static void assertNotAdjacent(WorkingSet source, WorkingSet target) {
        BindingFailure failure = assertThrows(BindingFailure.class,
                () -> WorkingSetLineagePolicy.comparableBindings(source, target));
        assertEquals(Reason.WorkingSetsNotAdjacent, failure.reason());
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

        assertEquals(List.of(unshared), WorkingSetLineagePolicy.comparableBindings(root, child));
        assertEquals(List.of(unshared), WorkingSetLineagePolicy.comparableBindings(child, root));
        assertTrue(WorkingSetLineagePolicy.comparableBindings(child, grandchild).isEmpty());
    }

    @Test
    void directDerivationRequiresChildSourceIdToBeTheImmediateParent() {
        ProductSpace productSpace = new ProductSpace("259944010602432");
        Classification classification = new Classification("266673196957632", SharingMode.UNSHARED);
        Tracker parent = Tracker.original(TrackerId.of("260634912653248"), productSpace, classification, null);
        Tracker child = Tracker.derived(TrackerId.of("259712159403968"), parent, childId);
        Tracker sibling = Tracker.derived(TrackerId.of("263561533034432"), parent, childId);
        Tracker grandchild = Tracker.derived(TrackerId.of("265077320994752"), child, grandchildId);

        assertTrue(WorkingSetLineagePolicy.isDirectDerivation(child, parent));
        assertFalse(WorkingSetLineagePolicy.isDirectDerivation(grandchild, parent));
        assertFalse(WorkingSetLineagePolicy.isDirectDerivation(sibling, child));
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
