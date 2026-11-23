package bootstrap;


import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetCreationController.CreationView;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.event.*;
import com.airedgesoft.sharedkernel.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(T7RelationEventAcceptanceTest.RelationConfiguration.class)
class T7RelationEventAcceptanceTest {
    private final String suffix = UUID.randomUUID().toString();
    private final String space = fixtureId("space-" + suffix);
    private final String classification = "14010101";
    private final String parentTracker = fixtureId("parent-" + suffix);
    private final String child = fixtureId("child-" + suffix);
    private final String parentItem = fixtureId("item-" + suffix);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelationEvents relationEvents;

    @AfterEach
    void cleanup() {
        relationEvents.clear();
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + child + "%");
        jdbc.update("DELETE FROM air_ws_items_index WHERE workingset_id = ?", child);
        jdbc.update("DELETE FROM air_ws_items_index WHERE branch_id = ?", parentTracker);
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", child);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM air_inst_requirement_rev WHERE tracker = ?", parentTracker);
        jdbc.update("DELETE FROM air_inst_common WHERE id = ?", parentItem);
        jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
    }

    @Test
    void extensionPublishesReferenceAssociationAndDanglingRepairEvents() {
        seedParentRange();
        FixtureIds.insertWorkingSet(jdbc, "1", child);
        ResponseEntity<CreationView> accepted = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", "extension-" + suffix, "trackerIds", List.of(parentTracker)), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            CreationView task = creation(accepted.getBody().creationId());
            assertEquals(WorkingSetCreation.Status.Succeeded, task.status(), () -> task.failures().toString());
        });

        assertEquals(1, relationEvents.references().size());
        assertEquals(1, relationEvents.associations().size());
        assertEquals(1, relationEvents.repairs().size());
        assertEquals(OriginType.EXTENSION, relationEvents.references().getFirst().metadata().originType());
        assertEquals("reference-" + parentItem, relationEvents.references().getFirst().metadata().originalRelationId());
        assertEquals("association-" + parentItem, relationEvents.associations().getFirst().metadata().originalRelationId());
        assertNotNull(relationEvents.repairs().getFirst().target().localId());
    }

    private void seedParentRange() {
        jdbc.update("INSERT INTO product_spaces VALUES (?)", space);
        FixtureIds.insertClassification(jdbc, classification, "UNSHARED");
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, parentTracker, space, classification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode, is_default)
                VALUES (?, '1', ?, 10070101, 'UNSHARED', TRUE)
                """, fixtureId("folder-" + suffix), space);
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version,
                    working_set_id, created_by, created_time)
                VALUES (?, ?, ?, 'source', 'description', 3, 0,
                    (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?), 'test', ?)
                """, parentItem, parentItem, parentTracker, parentTracker, System.currentTimeMillis());
        ItemIndexFixture.registerRequirementFacts(jdbc, parentItem);
        ItemIndexFixture.insert(jdbc, parentItem, parentItem, parentTracker, "1", 3, parentItem, 3, true);
    }

    private CreationView creation(String id) {
        return rest.getForObject("/api/working-set-creations/" + id, CreationView.class);
    }

    @TestConfiguration
    static class RelationConfiguration {
        @Bean
        @Primary
        RelationSourceQueryProvider relationSource() {
            return new RelationSourceQueryProvider() {
                @Override
                public List<ReferenceFact> upstreamReferences(ReferenceEndpointEvent source) {
                    return List.of(new ReferenceFact("reference-" + Long.toString(source.commonId()), source, target()));
                }

                @Override
                public List<AssociationFact> upstreamAssociations(AssociationEndpointEvent source) {
                    return List.of(new AssociationFact("association-" + Long.toString(source.commonId()), source,
                            associationTarget()));
                }

                @Override
                public List<ReferenceFact> referencesTargeting(ReferenceEndpointEvent target) {
                    return List.of(new ReferenceFact("repair-" + Long.toString(target.commonId()),
                            referenceSource(), target));
                }

                @Override
                public List<AssociationFact> associationsTargeting(AssociationEndpointEvent target) {
                    return List.of();
                }

                private ReferenceEndpointEvent target() {
                    return new ReferenceEndpointEvent(WorkingSet.DEFAULT_ID, Long.valueOf("262422876747712"),
                            Long.valueOf("263710506950592"), Long.valueOf("263244057497536"), 4);
                }

                private AssociationEndpointEvent associationTarget() {
                    return new AssociationEndpointEvent(WorkingSet.DEFAULT_ID, Long.valueOf("262422876747712"),
                            Long.valueOf("263710506950592"), Long.valueOf("263244057497536"));
                }

                private ReferenceEndpointEvent referenceSource() {
                    return new ReferenceEndpointEvent(WorkingSet.DEFAULT_ID, Long.valueOf("267169595373504"),
                            Long.valueOf("260782703683520"), Long.valueOf("266102343897024"), 1);
                }
            };
        }

        @Bean
        RelationEvents relationEvents() {
            return new RelationEvents();
        }
    }

    static class RelationEvents {
        private final List<ReferenceCreationRequestedEvent> references = new CopyOnWriteArrayList<>();
        private final List<AssociationCreationRequestedEvent> associations = new CopyOnWriteArrayList<>();
        private final List<DanglingReferenceRepairRequestedEvent> repairs = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(ReferenceCreationRequestedEvent event) {
            references.add(event);
        }

        @EventListener
        public void on(AssociationCreationRequestedEvent event) {
            associations.add(event);
        }

        @EventListener
        public void on(DanglingReferenceRepairRequestedEvent event) {
            repairs.add(event);
        }

        List<ReferenceCreationRequestedEvent> references() {
            return references;
        }

        List<AssociationCreationRequestedEvent> associations() {
            return associations;
        }

        List<DanglingReferenceRepairRequestedEvent> repairs() {
            return repairs;
        }

        void clear() {
            references.clear();
            associations.clear();
            repairs.clear();
        }
    }
}
