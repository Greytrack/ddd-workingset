package sk.workingset.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private final WorkingSetId childId = WorkingSetId.of("child");
    private final WorkingSetId grandchildId = WorkingSetId.of("grandchild");

    @Test
    void allowsDirectParentChildInEitherDirectionAndExcludesSharedTrackers() {
        var shared = new TrackerBinding(childId, TrackerId.of("shared"), "space", "shared-class",
                SharingMode.SHARED, TrackerOrigin.SHARED_REUSE);
        var unshared = new TrackerBinding(childId, TrackerId.of("unshared"), "space", "unshared-class",
                SharingMode.UNSHARED, TrackerOrigin.UNSHARED_DERIVATION);
        var space = new SpaceBinding(childId, "space", SpaceOrigin.EXPLICIT, SpaceState.ACTIVE);
        var child = new WorkingSet(childId, root.id(), List.of(space), List.of(shared, unshared));
        var grandchild = WorkingSet.child(grandchildId, child.id());

        assertEquals(List.of(unshared), WorkingSetLineagePolicy.comparableBindings(root, child));
        assertEquals(List.of(unshared), WorkingSetLineagePolicy.comparableBindings(child, root));
        assertTrue(WorkingSetLineagePolicy.comparableBindings(child, grandchild).isEmpty());
    }

    @Test
    void rejectsSiblingsAndNonAdjacentAncestorsInEitherDirection() {
        var child = WorkingSet.child(childId, root.id());
        var sibling = WorkingSet.child(WorkingSetId.of("sibling"), root.id());
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
