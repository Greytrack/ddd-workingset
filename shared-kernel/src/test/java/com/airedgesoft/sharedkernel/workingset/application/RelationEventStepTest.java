package com.airedgesoft.sharedkernel.workingset.application;


import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.command.RelationEventPublisher;
import com.airedgesoft.sharedkernel.workingset.application.command.RelationEventStep;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemSummary;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider.AssociationFact;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider.ReferenceFact;
import com.airedgesoft.sharedkernel.workingset.domain.Classification;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.event.*;
import com.airedgesoft.sharedkernel.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet.TrackerBinding;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet.TrackerOrigin;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationEventStepTest {
    private final Long target = Long.valueOf("259712159403968");
    private final Long parentId = Long.valueOf("265625955250112");
    private final Long childId = Long.valueOf("261538396602304");

    @Test
    void publishesCreationAssociationAndRepairForEveryIndexedItem() {
        Tracker parent = Tracker.original(parentId, new ProductSpace("259944010602432"),
                new Classification(266673196957632L, SharingMode.UNSHARED, "测试分类", 11L, 111L), null);
        Tracker child = Tracker.derived(childId, parent, target, true);
        WorkingSet workingSet = new WorkingSet(target, WorkingSet.DEFAULT_ID, List.of(),
                List.of(new TrackerBinding(target, childId, "259944010602432", "266673196957632", SharingMode.UNSHARED,
                        TrackerOrigin.UNSHARED_DERIVATION)));
        LightTrackerItem item = LightTrackerItem.derive(Long.valueOf("261158455371712"), Long.valueOf("265506033508288"), childId, target, 3,
                Long.valueOf("260574482192320"), 3, 42L, 14010101L);

        TrackerRepository trackers = mock(TrackerRepository.class);
        WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
        LightTrackerItemQueryProvider itemQueries = mock(LightTrackerItemQueryProvider.class);
        RelationSourceQueryProvider source = mock(RelationSourceQueryProvider.class);
        when(trackers.findDerivedTracker(target, parentId)).thenReturn(Optional.of(child));
        when(workingSets.findByIdForCreation(target)).thenReturn(Optional.of(workingSet));
        when(itemQueries.findInTracker(childId)).thenReturn(List.of(LightTrackerItemSummary.from(item)));
        ReferenceEndpointEvent targetEndpoint = new ReferenceEndpointEvent(Long.valueOf("260634912653248"), Long.valueOf("265864557082560"),
                Long.valueOf("261349487529920"), Long.valueOf("259649290751936"), 4);
        ReferenceEndpointEvent sourceEndpoint = new ReferenceEndpointEvent(target, item.localId(), item.commonId(), childId, item.revision());
        when(source.upstreamReferences(any())).thenReturn(List.of(new ReferenceFact("reference-1", sourceEndpoint,
                targetEndpoint)));
        when(source.upstreamAssociations(any())).thenReturn(List.of(new AssociationFact("association-1",
                new AssociationEndpointEvent(target, item.localId(), item.commonId(), childId),
                new AssociationEndpointEvent(Long.valueOf("260634912653248"), Long.valueOf("265864557082560"),
                        Long.valueOf("261349487529920"), Long.valueOf("259649290751936")))));
        when(source.referencesTargeting(any())).thenReturn(List.of(new ReferenceFact("reference-2", sourceEndpoint,
                targetEndpoint)));

        ArrayList<Object> published = new ArrayList<Object>();
        RelationEventPublisher publisher = new RelationEventPublisher(published::add);
        RelationEventStep step = new RelationEventStep(trackers, workingSets, itemQueries, source, publisher);

        RelationEventStep.StepResult result = step.publish(target, parentId, OriginType.EXTENSION, "extension-1");

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
                new Classification(266673196957632L, SharingMode.UNSHARED, "测试分类", 11L, 111L), null);
        Tracker child = Tracker.derived(childId, parent, target, true);
        WorkingSet workingSet = new WorkingSet(target, WorkingSet.DEFAULT_ID, List.of(),
                List.of(new TrackerBinding(target, childId, "259944010602432", "266673196957632", SharingMode.UNSHARED,
                        TrackerOrigin.UNSHARED_DERIVATION)));
        LightTrackerItem item = LightTrackerItem.derive(Long.valueOf("261158455371712"), Long.valueOf("265506033508288"), childId, target, 3,
                Long.valueOf("260574482192320"), 3, 42L, 14010101L);

        TrackerRepository trackers = mock(TrackerRepository.class);
        WorkingSetRepository workingSets = mock(WorkingSetRepository.class);
        LightTrackerItemQueryProvider itemQueries = mock(LightTrackerItemQueryProvider.class);
        RelationSourceQueryProvider source = mock(RelationSourceQueryProvider.class);
        when(trackers.findDerivedTracker(target, parentId)).thenReturn(Optional.of(child));
        when(workingSets.findByIdForCreation(target)).thenReturn(Optional.of(workingSet));
        when(itemQueries.findInTracker(childId)).thenReturn(List.of(LightTrackerItemSummary.from(item)));
        when(source.upstreamReferences(any())).thenThrow(new IllegalStateException("source unavailable"));

        RelationEventStep step = new RelationEventStep(trackers, workingSets, itemQueries, source,
                new RelationEventPublisher(event -> {
                }));

        RelationEventStep.StepResult result = step.publish(target, parentId, OriginType.EXTENSION, "extension-1");

        assertEquals(0, result.published());
        assertEquals(1, result.failed());
        assertFalse(result.succeeded());
    }
}
