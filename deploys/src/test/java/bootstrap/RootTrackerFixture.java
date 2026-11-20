package bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;

import static bootstrap.FixtureIds.fixtureId;

/** 最小平台登记数据，供既有 Requirement 与 COW 验收使用。 */
record RootTrackerFixture(JdbcTemplate jdbc, String trackerId) {
    private String spaceId() { return fixtureId(trackerId + "-s"); }
    private String classificationId() { return fixtureId(trackerId + "-c"); }

    void provision() {
        jdbc.update("INSERT INTO product_spaces VALUES (?)", spaceId());
        FixtureIds.insertClassification(jdbc, classificationId(), "UNSHARED");
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, trackerId, spaceId(), classificationId());
    }

    void remove() {
        jdbc.update("DELETE FROM light_tracker_items WHERE tracker_id = ?", trackerId);
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", trackerId);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", spaceId());
        jdbc.update("DELETE FROM classifications WHERE classification_id = ?", classificationId());
    }

    void removeRequirement(String localId) {
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + localId + "%");
        jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", localId);
        jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", localId);
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", localId);
    }
}
