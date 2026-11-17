package sk.workingset.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSet.SpaceBinding;
import sk.workingset.domain.WorkingSet.SpaceOrigin;
import sk.workingset.domain.WorkingSet.SpaceState;
import sk.workingset.domain.WorkingSet.TrackerBinding;
import sk.workingset.domain.WorkingSet.TrackerOrigin;

class WorkingSetLineagePolicyTest {
    private final WorkingSet root = new WorkingSet(WorkingSet.DEFAULT_ID, null, List.of(), List.of());
    private final WorkingSetId childId = WorkingSetId.of("259712159403968");
    private final WorkingSetId grandchildId = WorkingSetId.of("265077320994752");

    @Test
    void allowsDirectParentChildInEitherDirectionAndExcludesSharedTrackers() {
        var shared = new TrackerBinding(childId, TrackerId.of("259191613022144"), "259944010602432", "258520000000031",
                SharingMode.SHARED, TrackerOrigin.SHARED_REUSE);
        var unshared = new TrackerBinding(childId, TrackerId.of("267052500774848"), "259944010602432", "258520000000032",
                SharingMode.UNSHARED, TrackerOrigin.UNSHARED_DERIVATION);
        var space = new SpaceBinding(childId, "259944010602432", SpaceOrigin.EXPLICIT, SpaceState.ACTIVE);
        var child = new WorkingSet(childId, root.id(), List.of(space), List.of(shared, unshared));
        var grandchild = WorkingSet.child(grandchildId, child.id());

        assertEquals(List.of(unshared), WorkingSetLineagePolicy.comparableBindings(root, child));
        assertEquals(List.of(unshared), WorkingSetLineagePolicy.comparableBindings(child, root));
        assertTrue(WorkingSetLineagePolicy.comparableBindings(child, grandchild).isEmpty());
    }

    @Test
    void directDerivationRequiresChildSourceIdToBeTheImmediateParent() {
        var productSpace = new ProductSpace("259944010602432");
        var classification = new Classification("266673196957632", SharingMode.UNSHARED);
        var parent = Tracker.original(TrackerId.of("260634912653248"), productSpace, classification, null);
        var child = Tracker.derived(TrackerId.of("259712159403968"), parent, childId);
        var sibling = Tracker.derived(TrackerId.of("263561533034432"), parent, childId);
        var grandchild = Tracker.derived(TrackerId.of("265077320994752"), child, grandchildId);

        assertTrue(WorkingSetLineagePolicy.isDirectDerivation(child, parent));
        assertFalse(WorkingSetLineagePolicy.isDirectDerivation(grandchild, parent));
        assertFalse(WorkingSetLineagePolicy.isDirectDerivation(sibling, child));
    }

    @Test
    void rejectsSiblingsAndNonAdjacentAncestorsInEitherDirection() {
        var child = WorkingSet.child(childId, root.id());
        var sibling = WorkingSet.child(WorkingSetId.of("263561533034432"), root.id());
        var grandchild = WorkingSet.child(grandchildId, child.id());

        assertNotAdjacent(child, sibling);
        assertNotAdjacent(root, grandchild);
        assertNotAdjacent(grandchild, root);
        assertNotAdjacent(child, child);
    }

    private static void assertNotAdjacent(WorkingSet source, WorkingSet target) {
        var failure = assertThrows(BindingFailure.class,
                () -> WorkingSetLineagePolicy.comparableBindings(source, target));
        assertEquals(Reason.WorkingSetsNotAdjacent, failure.reason());
    }
}
