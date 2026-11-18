package sk.workingset.application;

import sk.workingset.application.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sk.workingset.domain.event.AssociationCreationRequestedEvent;
import sk.workingset.domain.event.DanglingReferenceRepairRequestedEvent;
import sk.workingset.domain.event.ReferenceCreationRequestedEvent;
import sk.workingset.domain.event.ReferenceEndpointEvent;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.application.query.provider.RelationSourcePort;
import sk.workingset.application.query.provider.RelationSourcePort.AssociationFact;
import sk.workingset.application.query.provider.RelationSourcePort.ReferenceFact;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
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
    private final WorkingSetId target = WorkingSetId.of("259712159403968");
    private final TrackerId parentId = TrackerId.of("265625955250112");
    private final TrackerId childId = TrackerId.of("261538396602304");

    @Test
    void publishesCreationAssociationAndRepairForEveryIndexedItem() {
        Tracker parent = Tracker.original(parentId, new ProductSpace("259944010602432"),
                new Classification("266673196957632", SharingMode.UNSHARED), null);
        Tracker child = Tracker.derived(childId, parent, target, true);
        WorkingSet workingSet = new WorkingSet(target, WorkingSet.DEFAULT_ID, List.of(),
                List.of(new TrackerBinding(target, childId, "259944010602432", "266673196957632", SharingMode.UNSHARED,
                        TrackerOrigin.UNSHARED_DERIVATION)));
        LightTrackerItem item = LightTrackerItem.derive(LocalId.of("261158455371712"), CommonId.of("265506033508288"), childId, target, Revision.of(3),
                LocalId.of("260574482192320"), Revision.of(3));

        TrackerRepository trackers = mock(TrackerRepository.class);
        WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
        LightTrackerItemRepository items = mock(LightTrackerItemRepository.class);
        RelationSourcePort source = mock(RelationSourcePort.class);
        when(trackers.findDerivedTracker(target, parentId)).thenReturn(Optional.of(child));
        when(workingSets.findWorkingSetForCreation(target)).thenReturn(Optional.of(workingSet));
        when(items.findInTracker(childId)).thenReturn(List.of(item));
        ReferenceEndpointEvent targetEndpoint = new ReferenceEndpointEvent(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"),
                CommonId.of("261349487529920"), TrackerId.of("259649290751936"), Revision.of(4));
        ReferenceEndpointEvent sourceEndpoint = new ReferenceEndpointEvent(target, item.localId(), item.commonId(), childId, item.revision());
        when(source.upstreamReferences(any())).thenReturn(List.of(new ReferenceFact("reference-1", sourceEndpoint,
                targetEndpoint)));
        when(source.upstreamAssociations(any())).thenReturn(List.of(new AssociationFact("association-1",
                new sk.workingset.domain.event.AssociationEndpointEvent(target, item.localId(), item.commonId(), childId),
                new sk.workingset.domain.event.AssociationEndpointEvent(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"),
                        CommonId.of("261349487529920"), TrackerId.of("259649290751936")))));
        when(source.referencesTargeting(any())).thenReturn(List.of(new ReferenceFact("reference-2", sourceEndpoint,
                targetEndpoint)));

        ArrayList<Object> published = new ArrayList<Object>();
        sk.workingset.application.command.RelationEventPublisher publisher = new RelationEventPublisher(published::add, entity -> 10L);
        sk.workingset.application.command.RelationEventStep step = new RelationEventStep(trackers, workingSets, items, source, publisher);

        sk.workingset.application.command.RelationEventStep.StepResult result = step.publish(target, parentId, OriginType.EXTENSION, "extension-1");

        assertEquals(3, result.published());
        assertEquals(0, result.failed());
        assertEquals(3, published.size());
        assertTrue(published.get(0) instanceof ReferenceCreationRequestedEvent);
        assertTrue(published.get(1) instanceof AssociationCreationRequestedEvent);
        assertTrue(published.get(2) instanceof DanglingReferenceRepairRequestedEvent);
    }

    @Test
    void recordsFailureWithoutThrowingWhenRelationSourceFails() {
        Tracker parent = Tracker.original(parentId, new ProductSpace("259944010602432"),
                new Classification("266673196957632", SharingMode.UNSHARED), null);
        Tracker child = Tracker.derived(childId, parent, target, true);
        WorkingSet workingSet = new WorkingSet(target, WorkingSet.DEFAULT_ID, List.of(),
                List.of(new TrackerBinding(target, childId, "259944010602432", "266673196957632", SharingMode.UNSHARED,
                        TrackerOrigin.UNSHARED_DERIVATION)));
        LightTrackerItem item = LightTrackerItem.derive(LocalId.of("261158455371712"), CommonId.of("265506033508288"), childId, target, Revision.of(3),
                LocalId.of("260574482192320"), Revision.of(3));

        TrackerRepository trackers = mock(TrackerRepository.class);
        WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
        LightTrackerItemRepository items = mock(LightTrackerItemRepository.class);
        RelationSourcePort source = mock(RelationSourcePort.class);
        when(trackers.findDerivedTracker(target, parentId)).thenReturn(Optional.of(child));
        when(workingSets.findWorkingSetForCreation(target)).thenReturn(Optional.of(workingSet));
        when(items.findInTracker(childId)).thenReturn(List.of(item));
        when(source.upstreamReferences(any())).thenThrow(new IllegalStateException("source unavailable"));

        sk.workingset.application.command.RelationEventStep step = new RelationEventStep(trackers, workingSets, items, source,
                new RelationEventPublisher(event -> { }, entity -> 1L));

        sk.workingset.application.command.RelationEventStep.StepResult result = step.publish(target, parentId, OriginType.EXTENSION, "extension-1");

        assertEquals(0, result.published());
        assertEquals(1, result.failed());
        assertFalse(result.succeeded());
    }
}
