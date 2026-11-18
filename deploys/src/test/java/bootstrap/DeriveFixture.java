package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * 测试夹具：直接建立一个非共享派生 Tracker 及其 Source-backed LTI。
 *
 * <p>仅用于 COW、Merge、绑定等用例的前置数据；生产代码不再提供单 Item 派生入口。
 * 目录范围与共享模式检查在这里显式执行，避免夹具绕过被测绑定规则。
 */
final class DeriveFixture {
    private DeriveFixture() {}

    static Result derive(JdbcTemplate jdbc, String targetWorkingSet, String sourceTrackerId,
                         String sourceLocalId, Revision revision, CommonId commonId) {
        var source = tracker(jdbc, sourceTrackerId);
        var targetParent = jdbc.queryForObject(
                "SELECT parent_working_set_id FROM working_sets WHERE working_set_id = ?",
                String.class, Long.valueOf(targetWorkingSet));
        if (!"1".equals(targetParent) && !exists(jdbc, """
                SELECT 1 FROM tracker_bindings WHERE working_set_id = ? AND tracker_id = ?
                """, Long.valueOf(targetParent), Long.valueOf(sourceTrackerId))) {
            throw new BindingFailure(BindingFailure.Reason.SourceTrackerNotFound, sourceTrackerId);
        }
        if (!"UNSHARED".equals(source.get("sharing_mode"))) {
            throw new BindingFailure(BindingFailure.Reason.SharingModeMismatch, sourceTrackerId);
        }
        if (exists(jdbc, """
                SELECT 1 FROM tracker_bindings
                WHERE working_set_id = ? AND tracker_id IN
                    (SELECT tracker_id FROM trackers WHERE source_id = ?)
                """, Long.valueOf(targetWorkingSet), Long.valueOf(sourceTrackerId))) {
            throw new BindingFailure(BindingFailure.Reason.TrackerAlreadyBound, sourceTrackerId);
        }
        if (!exists(jdbc, "SELECT 1 FROM space_bindings WHERE working_set_id = ? AND product_space_id = ?",
                Long.valueOf(targetWorkingSet), Long.valueOf(source.get("product_space_id").toString()))) {
            throw new BindingFailure(BindingFailure.Reason.MissingSpaceBinding, targetWorkingSet);
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
        jdbc.update("""
                INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                    source_local_id, source_revision, materialized)
                VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)
                """, Long.valueOf(localId), Long.valueOf(commonId.value()), Long.valueOf(trackerId),
                Long.valueOf(targetWorkingSet), revision.value(), Long.valueOf(content.localId()),
                content.revision().value());

        var item = LightTrackerItem.restore(LocalId.of(localId), commonId, TrackerId.of(trackerId),
                WorkingSetId.of(targetWorkingSet), revision, LocalId.of(content.localId()),
                content.revision(), false);
        return new Result(trackerId, sourceTrackerId, item);
    }

    private static Map<String, Object> tracker(JdbcTemplate jdbc, String trackerId) {
        var rows = jdbc.queryForList("""
                SELECT name, source_id, product_space_id, classification_id, sharing_mode
                FROM trackers WHERE tracker_id = ?
                """, Long.valueOf(trackerId));
        if (rows.isEmpty()) {
            throw new BindingFailure(BindingFailure.Reason.SourceTrackerNotFound, trackerId);
        }
        return rows.getFirst();
    }

    private static Content content(JdbcTemplate jdbc, String sourceTrackerId, String sourceLocalId,
                                   CommonId commonId, Revision revision) {
        boolean derived = jdbc.queryForObject("SELECT source_id IS NOT NULL FROM trackers WHERE tracker_id = ?",
                Boolean.class, Long.valueOf(sourceTrackerId));
        if (derived) {
            var rows = jdbc.queryForList("""
                    SELECT tracker_id, common_id, revision, source_local_id, source_revision, materialized
                    FROM light_tracker_items WHERE local_id = ?
                    """, Long.valueOf(sourceLocalId));
            if (rows.isEmpty()) {
                throw new BindingFailure(BindingFailure.Reason.SourceItemNotFound, sourceLocalId);
            }
            var row = rows.getFirst();
            if (!sourceTrackerId.equals(row.get("tracker_id").toString())
                    || !commonId.value().equals(row.get("common_id").toString())
                    || revision.value() != ((Number) row.get("revision")).intValue()) {
                throw new BindingFailure(BindingFailure.Reason.SourceItemMismatch, sourceLocalId);
            }
            boolean materialized = (Boolean) row.get("materialized");
            return new Content(materialized ? sourceLocalId : row.get("source_local_id").toString(),
                    Revision.of(((Number) (materialized ? row.get("revision") : row.get("source_revision"))).intValue()));
        }
        var rows = jdbc.queryForList("""
                SELECT tracker_id, common_id FROM requirements WHERE local_id = ?
                """, Long.valueOf(sourceLocalId));
        if (rows.isEmpty()) {
            throw new BindingFailure(BindingFailure.Reason.SourceItemNotFound, sourceLocalId);
        }
        var row = rows.getFirst();
        if (!sourceTrackerId.equals(row.get("tracker_id").toString())
                || !commonId.value().equals(row.get("common_id").toString())) {
            throw new BindingFailure(BindingFailure.Reason.SourceItemMismatch, sourceLocalId);
        }
        return new Content(sourceLocalId, revision);
    }

    private static boolean exists(JdbcTemplate jdbc, String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + sql + ")", Boolean.class, args));
    }

    record Result(String trackerId, String sourceTrackerId, LightTrackerItem item) {}

    private record Content(String localId, Revision revision) {}
}
