package sk.workingset.application;

import org.junit.jupiter.api.Test;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.RelationEventPublisher;
import sk.workingset.application.query.provider.RelationSourceQueryProvider.AssociationFact;
import sk.workingset.application.query.provider.RelationSourceQueryProvider.ReferenceFact;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.event.*;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationEventPublisherTest {
    private final List<Object> published = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(10);
    private final RelationEventPublisher publisher = new RelationEventPublisher(event -> published.add(event),
            entity -> sequence.incrementAndGet());

    private static ReferenceEndpointEvent source() {
        return new ReferenceEndpointEvent(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"), Revision.of(3));
    }

    private static ReferenceEndpointEvent target() {
        return new ReferenceEndpointEvent(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"), CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"), Revision.of(4));
    }

    private static AssociationEndpointEvent associationSource() {
        return new AssociationEndpointEvent(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"));
    }

    private static AssociationEndpointEvent associationTarget() {
        return new AssociationEndpointEvent(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"), CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"));
    }

    @Test
    void publishesReferenceCreationAndRepairWithBusinessRelationIdentity() {
        ReferenceFact fact = new ReferenceFact("reference-42", source(), target());

        List<ReferenceCreationRequestedEvent> creation = publisher.publishReferenceCreations("task-1", OriginType.CREATION, List.of(fact));
        List<DanglingReferenceRepairRequestedEvent> repair = publisher.publishReferenceRepairs("round-1", OriginType.MERGE, List.of(fact));

        assertEquals(2, published.size());
        assertTrue(published.get(0) instanceof ReferenceCreationRequestedEvent);
        assertTrue(published.get(1) instanceof DanglingReferenceRepairRequestedEvent);
        assertEquals("reference-42", creation.getFirst().metadata().originalRelationId());
        assertEquals("task-1", creation.getFirst().metadata().originId());
        assertEquals(OriginType.CREATION, creation.getFirst().metadata().originType());
        assertEquals("11", creation.getFirst().metadata().eventId());
        assertEquals("reference-42", repair.getFirst().metadata().originalRelationId());
        assertEquals("round-1", repair.getFirst().metadata().originId());
    }

    @Test
    void publishesAssociationCreationWithoutRevision() {
        AssociationFact fact = new AssociationFact("association-7", associationSource(), associationTarget());

        List<AssociationCreationRequestedEvent> created = publisher.publishAssociationCreations("task-1", OriginType.EXTENSION, List.of(fact));

        assertEquals(1, published.size());
        assertTrue(published.getFirst() instanceof AssociationCreationRequestedEvent);
        assertEquals("association-7", created.getFirst().metadata().originalRelationId());
        assertEquals("265625955250112", created.getFirst().source().trackerId().value());
        assertEquals("261538396602304", created.getFirst().target().trackerId().value());
    }
}
