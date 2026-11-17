package sk.workingset.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        var source = new ReferenceEndpoint(WorkingSetId.of("child"), LocalId.of("L1"), CommonId.of("C1"),
                TrackerId.of("T1"), Revision.of(3));
        var target = new ReferenceEndpoint(null, null, CommonId.of("C2"), TrackerId.of("T2"), null);

        var event = new ReferenceCreationRequested(metadata, source, target);

        assertEquals(source, event.source());
        assertEquals(target, event.target());
        assertEquals("reference-1", event.metadata().originalRelationId());
        assertFalse(target.isResolved());
    }

    @Test
    void referenceSourceMustBeFullyResolved() {
        var source = new ReferenceEndpoint(null, LocalId.of("L1"), CommonId.of("C1"), TrackerId.of("T1"),
                Revision.of(3));
        var target = new ReferenceEndpoint(null, null, CommonId.of("C2"), TrackerId.of("T2"), null);

        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceCreationRequested(metadata, source, target));
    }

    @Test
    void associationEventHasNoRevisionFields() {
        var source = new AssociationEndpoint(WorkingSetId.of("child"), LocalId.of("L1"), CommonId.of("C1"),
                TrackerId.of("T1"));
        var target = new AssociationEndpoint(WorkingSetId.of("parent"), LocalId.of("L2"), CommonId.of("C2"),
                TrackerId.of("T2"));

        var event = new AssociationCreationRequested(metadata, source, target);

        assertEquals("T1", event.source().trackerId().value());
        assertEquals("L2", event.target().localId().value());
    }

    @Test
    void metadataRejectsBlankIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new RelationEventMetadata("", "reference-1",
                "creation-1", RelationEventMetadata.OriginType.CREATION, Instant.now()));
    }
}
