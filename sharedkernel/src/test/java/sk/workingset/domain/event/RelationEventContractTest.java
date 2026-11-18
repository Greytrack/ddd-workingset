package sk.workingset.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

class RelationEventContractTest {

    private final RelationEventMetadataEvent metadata = new RelationEventMetadataEvent("event-1", "reference-1",
            "creation-1", RelationEventMetadataEvent.OriginType.CREATION, Instant.parse("2026-11-17T00:00:00Z"));

    @Test
    void referenceEventCarriesBothEndpointsAndAllowsDanglingTarget() {
        ReferenceEndpointEvent source = new ReferenceEndpointEvent(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"), Revision.of(3));
        ReferenceEndpointEvent target = new ReferenceEndpointEvent(WorkingSetId.of("260634912653248"), null, CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"), null);

        ReferenceCreationRequestedEvent event = new ReferenceCreationRequestedEvent(metadata, source, target);

        assertEquals(source, event.source());
        assertEquals(target, event.target());
        assertEquals("reference-1", event.metadata().originalRelationId());
        assertNull(target.localId());
    }

    @Test
    void referenceSourceMustBeFullyResolved() {
        ReferenceEndpointEvent source = new ReferenceEndpointEvent(WorkingSetId.of("259712159403968"), null, CommonId.of("265506033508288"), TrackerId.of("265625955250112"),
                Revision.of(3));
        ReferenceEndpointEvent target = new ReferenceEndpointEvent(WorkingSetId.of("260634912653248"), null, CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"), null);

        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceCreationRequestedEvent(metadata, source, target));
    }

    @Test
    void associationEventHasNoRevisionFields() {
        AssociationEndpointEvent source = new AssociationEndpointEvent(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"));
        AssociationEndpointEvent target = new AssociationEndpointEvent(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"), CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"));

        AssociationCreationRequestedEvent event = new AssociationCreationRequestedEvent(metadata, source, target);

        assertEquals("265625955250112", event.source().trackerId().value());
        assertEquals("265864557082560", event.target().localId().value());
    }

    @Test
    void referenceEndpointRequiresWorkingSetEvenWhenTargetLocalIsMissing() {
        assertThrows(NullPointerException.class,
                () -> new ReferenceEndpointEvent(null, null, CommonId.of("265506033508288"), TrackerId.of("265625955250112"), null));
    }

    @Test
    void metadataRejectsBlankIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new RelationEventMetadataEvent("", "reference-1",
                "creation-1", RelationEventMetadataEvent.OriginType.CREATION, Instant.now()));
    }
}
