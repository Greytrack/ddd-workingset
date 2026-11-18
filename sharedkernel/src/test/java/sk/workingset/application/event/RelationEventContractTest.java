package sk.workingset.application.event;

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

    private final RelationEventMetadata metadata = new RelationEventMetadata("event-1", "reference-1",
            "creation-1", RelationEventMetadata.OriginType.CREATION, Instant.parse("2026-11-17T00:00:00Z"));

    @Test
    void referenceEventCarriesBothEndpointsAndAllowsDanglingTarget() {
        var source = new ReferenceEndpoint(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"), Revision.of(3));
        var target = new ReferenceEndpoint(WorkingSetId.of("260634912653248"), null, CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"), null);

        var event = new ReferenceCreationRequested(metadata, source, target);

        assertEquals(source, event.source());
        assertEquals(target, event.target());
        assertEquals("reference-1", event.metadata().originalRelationId());
        assertNull(target.localId());
    }

    @Test
    void referenceSourceMustBeFullyResolved() {
        var source = new ReferenceEndpoint(WorkingSetId.of("259712159403968"), null, CommonId.of("265506033508288"), TrackerId.of("265625955250112"),
                Revision.of(3));
        var target = new ReferenceEndpoint(WorkingSetId.of("260634912653248"), null, CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"), null);

        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceCreationRequested(metadata, source, target));
    }

    @Test
    void associationEventHasNoRevisionFields() {
        var source = new AssociationEndpoint(WorkingSetId.of("259712159403968"), LocalId.of("261158455371712"), CommonId.of("265506033508288"),
                TrackerId.of("265625955250112"));
        var target = new AssociationEndpoint(WorkingSetId.of("260634912653248"), LocalId.of("265864557082560"), CommonId.of("261349487529920"),
                TrackerId.of("261538396602304"));

        var event = new AssociationCreationRequested(metadata, source, target);

        assertEquals("265625955250112", event.source().trackerId().value());
        assertEquals("265864557082560", event.target().localId().value());
    }

    @Test
    void referenceEndpointRequiresWorkingSetEvenWhenTargetLocalIsMissing() {
        assertThrows(NullPointerException.class,
                () -> new ReferenceEndpoint(null, null, CommonId.of("265506033508288"), TrackerId.of("265625955250112"), null));
    }

    @Test
    void metadataRejectsBlankIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new RelationEventMetadata("", "reference-1",
                "creation-1", RelationEventMetadata.OriginType.CREATION, Instant.now()));
    }
}
