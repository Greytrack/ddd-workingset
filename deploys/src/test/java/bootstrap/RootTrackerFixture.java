package bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;

import static bootstrap.FixtureIds.fixtureId;

/**
 * 最小平台登记数据，供既有 Requirement 与 COW 验收使用。
 */
record RootTrackerFixture(JdbcTemplate jdbc, String trackerId) {
    private static final String REQUIREMENT_CLASSIFICATION = "14010101";

    private String spaceId() {
        return fixtureId(trackerId + "-s");
    }

    void provision() {
        jdbc.update("INSERT INTO product_spaces VALUES (?)", spaceId());
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, trackerId, spaceId(), REQUIREMENT_CLASSIFICATION);
    }

    void remove() {
        jdbc.update("DELETE FROM air_ws_items_index WHERE branch_id = ?", trackerId);
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", trackerId);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", spaceId());
    }

    void removeRequirement(String localId) {
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + localId + "%");

        jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", localId);
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", localId);
    }
}
