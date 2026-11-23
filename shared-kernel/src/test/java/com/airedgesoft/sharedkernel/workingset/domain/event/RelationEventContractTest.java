package com.airedgesoft.sharedkernel.workingset.domain.event;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelationEventContractTest {

    private final RelationEventMetadataEvent metadata = new RelationEventMetadataEvent("event-1", "reference-1",
            "creation-1", RelationEventMetadataEvent.OriginType.CREATION, 1794873600000L);

    @Test
    void referenceEventCarriesBothEndpointsAndAllowsDanglingTarget() {
        ReferenceEndpointEvent source = new ReferenceEndpointEvent(Long.valueOf("259712159403968"), Long.valueOf("261158455371712"), Long.valueOf("265506033508288"),
                Long.valueOf("265625955250112"), 3);
        ReferenceEndpointEvent target = new ReferenceEndpointEvent(Long.valueOf("260634912653248"), null, Long.valueOf("261349487529920"),
                Long.valueOf("261538396602304"), null);

        ReferenceCreationRequestedEvent event = new ReferenceCreationRequestedEvent(metadata, source, target);

        assertEquals(source, event.source());
        assertEquals(target, event.target());
        assertEquals("reference-1", event.metadata().originalRelationId());
        assertNull(target.localId());
    }

    @Test
    void referenceSourceMustBeFullyResolved() {
        ReferenceEndpointEvent source = new ReferenceEndpointEvent(Long.valueOf("259712159403968"), null, Long.valueOf("265506033508288"), Long.valueOf("265625955250112"),
                3);
        ReferenceEndpointEvent target = new ReferenceEndpointEvent(Long.valueOf("260634912653248"), null, Long.valueOf("261349487529920"),
                Long.valueOf("261538396602304"), null);

        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceCreationRequestedEvent(metadata, source, target));
    }

    @Test
    void associationEventHasNoRevisionFields() {
        AssociationEndpointEvent source = new AssociationEndpointEvent(Long.valueOf("259712159403968"), Long.valueOf("261158455371712"), Long.valueOf("265506033508288"),
                Long.valueOf("265625955250112"));
        AssociationEndpointEvent target = new AssociationEndpointEvent(Long.valueOf("260634912653248"), Long.valueOf("265864557082560"), Long.valueOf("261349487529920"),
                Long.valueOf("261538396602304"));

        AssociationCreationRequestedEvent event = new AssociationCreationRequestedEvent(metadata, source, target);

        assertEquals("265625955250112", Long.toString(event.source().trackerId()));
        assertEquals("265864557082560", Long.toString(event.target().localId()));
    }

    @Test
    void referenceEndpointRequiresWorkingSetEvenWhenTargetLocalIsMissing() {
        assertThrows(NullPointerException.class,
                () -> new ReferenceEndpointEvent(null, null, Long.valueOf("265506033508288"), Long.valueOf("265625955250112"), null));
    }

    @Test
    void metadataRejectsBlankIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new RelationEventMetadataEvent("", "reference-1",
                "creation-1", RelationEventMetadataEvent.OriginType.CREATION, System.currentTimeMillis()));
    }
}
