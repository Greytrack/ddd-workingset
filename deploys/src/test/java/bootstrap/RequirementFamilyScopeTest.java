package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import alm.requirement.domain.Requirement;
import alm.requirement.infrastructure.repositories.JdbcRequirementRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;

/**
 * D2：Requirement 家族唯一性按 WorkingSet 作用域，而不是按 Tracker。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequirementFamilyScopeTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcRequirementRepository repository;

    private final String suffix = UUID.randomUUID().toString();
    private final String ws2 = fixtureId("WS2-" + suffix);
    private final String t1 = fixtureId("T1-" + suffix);
    private final String t2 = fixtureId("T2-" + suffix);
    private final String t3 = fixtureId("T3-" + suffix);
    private final String t4 = fixtureId("T4-" + suffix);
    private final String family = fixtureId("FAMILY-" + suffix);

    @BeforeEach
    void provision() {
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", ws2);
        for (String tracker : new String[] {t1, t2, t3, t4}) {
            jdbc.update("INSERT INTO product_spaces (product_space_id) VALUES (?)", fixtureId(tracker + "-space"));
            jdbc.update("INSERT INTO classifications (classification_id, sharing_mode) VALUES (?, 'UNSHARED')",
                    fixtureId(tracker + "-class"));
        }
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, t1, fixtureId(t1 + "-space"), fixtureId(t1 + "-class"));
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, t2, fixtureId(t2 + "-space"), fixtureId(t2 + "-class"));
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id,
                                      sharing_mode)
                VALUES (?, ?, ?, ?, ?, 'UNSHARED')
                """, t3, t1, ws2, fixtureId(t3 + "-space"), fixtureId(t3 + "-class"));
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id,
                                      sharing_mode)
                VALUES (?, ?, ?, ?, ?, 'UNSHARED')
                """, t4, t2, ws2, fixtureId(t4 + "-space"), fixtureId(t4 + "-class"));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM light_tracker_items WHERE common_id = ?", family);
        jdbc.update("DELETE FROM requirements WHERE common_id = ?", family);
        // Child trackers reference parents through source_id; remove them first.
        for (String tracker : new String[] {t3, t4, t2, t1}) {
            jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", tracker);
            jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", fixtureId(tracker + "-space"));
            jdbc.update("DELETE FROM classifications WHERE classification_id = ?", fixtureId(tracker + "-class"));
        }
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", ws2);
    }

    @Test
    void same_family_is_unique_within_a_working_set_but_allowed_across_working_sets() {
        assertDoesNotThrow(() -> repository.save(requirement("L1-", t1)));
        assertThrows(DataIntegrityViolationException.class, () -> repository.save(requirement("L2-", t2)));
        assertDoesNotThrow(() -> repository.save(requirement("L3-", t3)));
    }

    @Test
    void soft_deleted_row_keeps_family_identity_until_restored_or_purged() {
        Requirement first = requirement("L1-", t1);
        repository.save(first);
        repository.save(first.delete(first.lockVersion()));
        assertThrows(DataIntegrityViolationException.class, () -> repository.save(requirement("L2-", t1)));
    }

    @Test
    void deleted_requirements_are_excluded_from_tracker_catalog() {
        Requirement saved = requirement("L1-", t1);
        repository.save(saved);
        repository.save(saved.delete(saved.lockVersion()));
        assertTrue(repository.findIdentitiesByTracker(TrackerId.of(t1)).isEmpty());
    }

    @Test
    void light_tracker_item_family_is_unique_within_a_working_set() {
        assertDoesNotThrow(() -> insertVariant("V1-", t1, "1"));
        assertThrows(DataIntegrityViolationException.class, () -> insertVariant("V2-", t2, "1"));
        assertDoesNotThrow(() -> insertVariant("V3-", t3, ws2));
    }

    private void insertVariant(String prefix, String tracker, String workingSet) {
        String localId = fixtureId(prefix + suffix);
        jdbc.update("""
                INSERT INTO light_tracker_items
                    (local_id, common_id, tracker_id, working_set_id, revision,
                     source_local_id, source_revision, materialized)
                VALUES (?, ?, ?, ?, 3, ?, 3, true)
                """, localId, family, tracker, workingSet, localId);
    }

    private Requirement requirement(String prefix, String tracker) {
        return Requirement.create(LocalId.of(fixtureId(prefix + suffix)), CommonId.of(family), TrackerId.of(tracker),
                "A", "description", Revision.of(3));
    }
}
