package sk.workingset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import sk.workingset.application.event.AssociationCreationRequested;
import sk.workingset.application.event.DanglingReferenceRepairRequested;
import sk.workingset.application.event.ReferenceCreationRequested;
import sk.workingset.application.event.ReferenceEndpoint;
import sk.workingset.application.event.RelationEventMetadata.OriginType;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.RelationSourcePort;
import sk.workingset.application.port.RelationSourcePort.AssociationFact;
import sk.workingset.application.port.RelationSourcePort.ReferenceFact;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.Revision;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSet.TrackerBinding;
import sk.workingset.domain.WorkingSet.TrackerOrigin;

class RelationEventStepTest {
    private final WorkingSetId target = WorkingSetId.of("child");
    private final TrackerId parentId = TrackerId.of("T1");
    private final TrackerId childId = TrackerId.of("T2");

    @Test
    void publishesCreationAssociationAndRepairForEveryIndexedItem() {
        var parent = Tracker.original(parentId, new ProductSpace("space"),
                new Classification("class", SharingMode.UNSHARED), null);
        var child = Tracker.derived(childId, parent, target, true);
        var workingSet = new WorkingSet(target, WorkingSet.DEFAULT_ID, List.of(),
                List.of(new TrackerBinding(target, childId, "space", "class", SharingMode.UNSHARED,
                        TrackerOrigin.UNSHARED_DERIVATION)));
        var item = LightTrackerItem.derive(LocalId.of("L1"), CommonId.of("C1"), childId, target, Revision.of(3),
                LocalId.of("L0"), Revision.of(3));

        TrackerRepository trackers = mock(TrackerRepository.class);
        WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
        LightTrackerItemRepository items = mock(LightTrackerItemRepository.class);
        RelationSourcePort source = mock(RelationSourcePort.class);
        when(trackers.findDerivedTracker(target, parentId)).thenReturn(Optional.of(child));
        when(workingSets.findWorkingSetForCreation(target)).thenReturn(Optional.of(workingSet));
        when(items.findInTracker(childId)).thenReturn(List.of(item));
        var targetEndpoint = new ReferenceEndpoint(WorkingSetId.of("parent"), LocalId.of("L2"),
                CommonId.of("C2"), TrackerId.of("T3"), Revision.of(4));
        var sourceEndpoint = new ReferenceEndpoint(target, item.localId(), item.commonId(), childId, item.revision());
        when(source.upstreamReferences(any())).thenReturn(List.of(new ReferenceFact("reference-1", sourceEndpoint,
                targetEndpoint)));
        when(source.upstreamAssociations(any())).thenReturn(List.of(new AssociationFact("association-1",
                new sk.workingset.application.event.AssociationEndpoint(target, item.localId(), item.commonId(), childId),
                new sk.workingset.application.event.AssociationEndpoint(WorkingSetId.of("parent"), LocalId.of("L2"),
                        CommonId.of("C2"), TrackerId.of("T3")))));
        when(source.referencesTargeting(any())).thenReturn(List.of(new ReferenceFact("reference-2", sourceEndpoint,
                targetEndpoint)));

        var published = new ArrayList<Object>();
        var publisher = new RelationEventPublisher(published::add, new AtomicLong(10)::incrementAndGet);
        var step = new RelationEventStep(trackers, workingSets, items, source, publisher);

        var result = step.publish(target, parentId, OriginType.EXTENSION, "extension-1");

        assertEquals(3, result.published());
        assertEquals(0, result.failed());
        assertEquals(3, published.size());
        assertTrue(published.get(0) instanceof ReferenceCreationRequested);
        assertTrue(published.get(1) instanceof AssociationCreationRequested);
        assertTrue(published.get(2) instanceof DanglingReferenceRepairRequested);
    }

    @Test
    void recordsFailureWithoutThrowingWhenRelationSourceFails() {
        var parent = Tracker.original(parentId, new ProductSpace("space"),
                new Classification("class", SharingMode.UNSHARED), null);
        var child = Tracker.derived(childId, parent, target, true);
        var workingSet = new WorkingSet(target, WorkingSet.DEFAULT_ID, List.of(),
                List.of(new TrackerBinding(target, childId, "space", "class", SharingMode.UNSHARED,
                        TrackerOrigin.UNSHARED_DERIVATION)));
        var item = LightTrackerItem.derive(LocalId.of("L1"), CommonId.of("C1"), childId, target, Revision.of(3),
                LocalId.of("L0"), Revision.of(3));

        TrackerRepository trackers = mock(TrackerRepository.class);
        WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
        LightTrackerItemRepository items = mock(LightTrackerItemRepository.class);
        RelationSourcePort source = mock(RelationSourcePort.class);
        when(trackers.findDerivedTracker(target, parentId)).thenReturn(Optional.of(child));
        when(workingSets.findWorkingSetForCreation(target)).thenReturn(Optional.of(workingSet));
        when(items.findInTracker(childId)).thenReturn(List.of(item));
        when(source.upstreamReferences(any())).thenThrow(new IllegalStateException("source unavailable"));

        var step = new RelationEventStep(trackers, workingSets, items, source,
                new RelationEventPublisher(event -> { }, new AtomicLong(1)::incrementAndGet));

        var result = step.publish(target, parentId, OriginType.EXTENSION, "extension-1");

        assertEquals(0, result.published());
        assertEquals(1, result.failed());
        assertFalse(result.succeeded());
    }
}
