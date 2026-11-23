package bootstrap;


import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static bootstrap.FixtureIds.fixtureId;

/**
 * 测试夹具：直接建立一个非共享派生 Tracker 及其 Source-backed LTI。
 *
 * <p>仅用于 COW、Merge、绑定等用例的前置数据；生产代码不再提供单 Item 派生入口。
 * 目录范围与共享模式检查在这里显式执行，避免夹具绕过被测绑定规则。
 */
final class DeriveFixture {
    private DeriveFixture() {
    }

    static Result derive(JdbcTemplate jdbc, String targetWorkingSet, String sourceTrackerId,
                         String sourceLocalId, Integer revision, Long commonId) {
        Map<String, Object> source = tracker(jdbc, sourceTrackerId);
        String targetParent = jdbc.queryForObject(
                "SELECT parent_working_set_id FROM working_sets WHERE working_set_id = ?",
                String.class, Long.valueOf(targetWorkingSet));
        if (!"1".equals(targetParent) && !exists(jdbc, """
                SELECT 1 FROM tracker_bindings WHERE working_set_id = ? AND tracker_id = ?
                """, Long.valueOf(targetParent), Long.valueOf(sourceTrackerId))) {
            throw DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, sourceTrackerId);
        }
        if (!"UNSHARED".equals(source.get("sharing_mode"))) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, sourceTrackerId);
        }
        if (exists(jdbc, """
                SELECT 1 FROM tracker_bindings
                WHERE working_set_id = ? AND tracker_id IN
                    (SELECT tracker_id FROM trackers WHERE source_id = ?)
                """, Long.valueOf(targetWorkingSet), Long.valueOf(sourceTrackerId))) {
            throw DomainException.withMessage(BindingResCode.TRACKER_ALREADY_BOUND, sourceTrackerId);
        }
        if (!exists(jdbc, "SELECT 1 FROM space_bindings WHERE working_set_id = ? AND product_space_id = ?",
                Long.valueOf(targetWorkingSet), Long.valueOf(source.get("product_space_id").toString()))) {
            throw DomainException.withMessage(BindingResCode.MISSING_SPACE_BINDING, targetWorkingSet);
        }

        String trackerId = fixtureId("DERIVED-T-" + UUID.randomUUID());
        boolean isDefault = !exists(jdbc, """
                        SELECT 1 FROM trackers WHERE working_set_id = ? AND product_space_id = ?
                            AND classification_id = ? AND is_default
                        """, Long.valueOf(targetWorkingSet), Long.valueOf(source.get("product_space_id").toString()),
                Long.valueOf(source.get("classification_id").toString()));
        jdbc.update("""
                        INSERT INTO trackers (tracker_id, name, source_id, working_set_id, product_space_id,
                            classification_id, sharing_mode, is_default, visible)
                        VALUES (?, ?, ?, ?, ?, ?, 'UNSHARED', ?, TRUE)
                        """, Long.valueOf(trackerId), source.get("name"), Long.valueOf(sourceTrackerId),
                Long.valueOf(targetWorkingSet), Long.valueOf(source.get("product_space_id").toString()),
                Long.valueOf(source.get("classification_id").toString()), isDefault);
        jdbc.update("""
                        INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id,
                            classification_id, sharing_mode, origin)
                        VALUES (?, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION')
                        """, Long.valueOf(targetWorkingSet), Long.valueOf(trackerId),
                Long.valueOf(source.get("product_space_id").toString()),
                Long.valueOf(source.get("classification_id").toString()));

        Content content = content(jdbc, sourceTrackerId, sourceLocalId, commonId, revision);
        String localId = fixtureId("DERIVED-L-" + UUID.randomUUID());
        ItemIndexFixture.insert(jdbc, localId, Long.toString(commonId), trackerId, targetWorkingSet, revision,
                content.localId(), content.revision(), false);

        LightTrackerItem item = LightTrackerItem.restore(Long.valueOf(localId), commonId, Long.valueOf(trackerId),
                Long.valueOf(targetWorkingSet), revision, Long.valueOf(content.localId()),
                content.revision(), false,
                ((Number) source.get("product_space_id")).longValue(),
                ((Number) source.get("classification_id")).longValue());
        return new Result(trackerId, sourceTrackerId, item);
    }

    private static Map<String, Object> tracker(JdbcTemplate jdbc, String trackerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT name, source_id, product_space_id, classification_id, sharing_mode
                FROM trackers WHERE tracker_id = ?
                """, Long.valueOf(trackerId));
        if (rows.isEmpty()) {
            throw DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, trackerId);
        }
        return rows.getFirst();
    }

    private static Content content(JdbcTemplate jdbc, String sourceTrackerId, String sourceLocalId,
                                   Long commonId, Integer revision) {
        boolean derived = jdbc.queryForObject("SELECT source_id IS NOT NULL FROM trackers WHERE tracker_id = ?",
                Boolean.class, Long.valueOf(sourceTrackerId));
        if (derived) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT branch_id AS tracker_id, common_id, revision, source_local_id, source_revision,
                           (has_copied = 1) AS materialized
                    FROM air_ws_items_index WHERE local_id = ?
                    """, Long.valueOf(sourceLocalId));
            if (rows.isEmpty()) {
                throw DomainException.withMessage(BindingResCode.SOURCE_ITEM_NOT_FOUND, sourceLocalId);
            }
            Map<String, Object> row = rows.getFirst();
            if (!sourceTrackerId.equals(row.get("tracker_id").toString())
                    || !Long.toString(commonId).equals(row.get("common_id").toString())
                    || revision != ((Number) row.get("revision")).intValue()) {
                throw DomainException.withMessage(BindingResCode.SOURCE_ITEM_MISMATCH, sourceLocalId);
            }
            boolean materialized = (Boolean) row.get("materialized");
            return new Content(materialized ? sourceLocalId : row.get("source_local_id").toString(),
                    ((Number) (materialized ? row.get("revision") : row.get("source_revision"))).intValue());
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT tracker AS tracker_id, common_id FROM air_inst_requirement_rev WHERE instance_id = ?
                ORDER BY revision DESC LIMIT 1
                """, Long.valueOf(sourceLocalId));
        if (rows.isEmpty()) {
            throw DomainException.withMessage(BindingResCode.SOURCE_ITEM_NOT_FOUND, sourceLocalId);
        }
        Map<String, Object> row = rows.getFirst();
        if (!sourceTrackerId.equals(row.get("tracker_id").toString())
                || !Long.toString(commonId).equals(row.get("common_id").toString())) {
            throw DomainException.withMessage(BindingResCode.SOURCE_ITEM_MISMATCH, sourceLocalId);
        }
        return new Content(sourceLocalId, revision);
    }

    private static boolean exists(JdbcTemplate jdbc, String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + sql + ")", Boolean.class, args));
    }

    record Result(String trackerId, String sourceTrackerId, LightTrackerItem item) {
    }

    private record Content(String localId, Integer revision) {
    }
}
