package sk.workingset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import sk.workingset.application.event.AssociationCreationRequested;
import sk.workingset.application.event.DanglingReferenceRepairRequested;
import sk.workingset.application.event.ReferenceCreationRequested;
import sk.workingset.application.event.RelationEventMetadata.OriginType;
import sk.workingset.application.port.RelationSourcePort.AssociationFact;
import sk.workingset.application.port.RelationSourcePort.ReferenceFact;
import sk.workingset.application.event.AssociationEndpoint;
import sk.workingset.application.event.ReferenceEndpoint;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

class RelationEventPublisherTest {
    private final List<Object> published = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(10);
    private final RelationEventPublisher publisher = new RelationEventPublisher(event -> published.add(event),
            sequence::incrementAndGet);

    @Test
    void publishesReferenceCreationAndRepairWithBusinessRelationIdentity() {
        var fact = new ReferenceFact("reference-42", source(), target());

        var creation = publisher.publishReferenceCreations("task-1", OriginType.CREATION, List.of(fact));
        var repair = publisher.publishReferenceRepairs("round-1", OriginType.MERGE, List.of(fact));

        assertEquals(2, published.size());
        assertTrue(published.get(0) instanceof ReferenceCreationRequested);
        assertTrue(published.get(1) instanceof DanglingReferenceRepairRequested);
        assertEquals("reference-42", creation.getFirst().metadata().originalRelationId());
        assertEquals("task-1", creation.getFirst().metadata().originId());
        assertEquals(OriginType.CREATION, creation.getFirst().metadata().originType());
        assertEquals("11", creation.getFirst().metadata().eventId());
        assertEquals("reference-42", repair.getFirst().metadata().originalRelationId());
        assertEquals("round-1", repair.getFirst().metadata().originId());
    }

    @Test
    void publishesAssociationCreationWithoutRevision() {
        var fact = new AssociationFact("association-7", associationSource(), associationTarget());

        var created = publisher.publishAssociationCreations("task-1", OriginType.EXTENSION, List.of(fact));

        assertEquals(1, published.size());
        assertTrue(published.getFirst() instanceof AssociationCreationRequested);
        assertEquals("association-7", created.getFirst().metadata().originalRelationId());
        assertEquals("265625955250112", created.getFirst().source().trackerId().value());
        assertEquals("261538396602304", created.getFirst().target().trackerId().value());
    }

    private static ReferenceEndpoint source() {
        return new ReferenceEndpoint(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"), Revision.of(3));
    }

    private static ReferenceEndpoint target() {
        return new ReferenceEndpoint(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"), CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"), Revision.of(4));
    }

    private static AssociationEndpoint associationSource() {
        return new AssociationEndpoint(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"));
    }

    private static AssociationEndpoint associationTarget() {
        return new AssociationEndpoint(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"), CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"));
    }
}
