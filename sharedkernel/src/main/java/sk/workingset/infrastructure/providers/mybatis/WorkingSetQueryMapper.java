package sk.workingset.infrastructure.providers.mybatis;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import sk.workingset.application.query.model.ClassificationSummary;
import sk.workingset.application.query.model.SpaceBindingSummary;
import sk.workingset.application.query.model.TrackerBindingSummary;
import sk.workingset.application.query.model.TrackerSummary;
import sk.workingset.application.query.model.WorkingSetSummary;

/** Working Set 查询 Mapper；仅包含 SELECT。 */
@Mapper
public interface WorkingSetQueryMapper {

    @Select("""
            SELECT working_set_id, parent_working_set_id, name,
                   (working_set_id = 1) AS default_working_set
            FROM working_sets WHERE working_set_id = #{id}
            """)
    WorkingSetSummary find(@Param("id") long id);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM working_set_creations
                WHERE new_working_set_id = #{id} AND status IN ('Pending', 'Running')
                  AND COALESCE(result->'request'->>'mode', 'CREATE') = 'CREATE')
            """)
    boolean isPreparing(@Param("id") long id);

    @Select("""
            SELECT w.working_set_id FROM working_sets w
            WHERE w.parent_working_set_id = #{id}
              AND NOT EXISTS (
                  SELECT 1 FROM working_set_creations c
                  WHERE c.new_working_set_id = w.working_set_id
                    AND c.status IN ('Pending', 'Running')
                    AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
            ORDER BY w.working_set_id
            """)
    List<Long> findChildren(@Param("id") long id);

    @Select("""
            WITH RECURSIVE lineage(working_set_id, parent_working_set_id, distance) AS (
                SELECT working_set_id, parent_working_set_id, 0
                FROM working_sets WHERE working_set_id = #{id}
                UNION ALL
                SELECT p.working_set_id, p.parent_working_set_id, c.distance + 1
                FROM working_sets p JOIN lineage c ON p.working_set_id = c.parent_working_set_id
            ) CYCLE working_set_id SET cyclic USING path
            SELECT l.working_set_id FROM lineage l
            WHERE l.distance > 0 AND NOT EXISTS (
                SELECT 1 FROM working_set_creations c
                WHERE c.new_working_set_id = l.working_set_id
                  AND c.status IN ('Pending', 'Running')
                  AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
            ORDER BY l.distance
            """)
    List<Long> findAncestors(@Param("id") long id);

    @Select("""
            SELECT t.tracker_id, t.name, t.parent_id, t.source_id, t.product_space_id,
                   t.classification_id, t.sharing_mode, t.is_default
            FROM trackers t
            WHERE t.visible AND (t.working_set_id = #{id} OR EXISTS (
                SELECT 1 FROM tracker_bindings b
                WHERE b.working_set_id = #{id} AND b.tracker_id = t.tracker_id))
            ORDER BY t.tracker_id
            """)
    List<TrackerSummary> findTrackers(@Param("id") long id);

    @Select("""
            SELECT classification_id, sharing_mode, name
            FROM classifications ORDER BY classification_id
            """)
    List<ClassificationSummary> findClassifications();

    @Select("""
            SELECT sb.product_space_id, ps.name, sb.origin, sb.state
            FROM space_bindings sb
            LEFT JOIN product_spaces ps ON ps.product_space_id = sb.product_space_id
            WHERE sb.working_set_id = #{id}
            ORDER BY sb.product_space_id
            """)
    List<SpaceBindingSummary> findSpaceBindings(@Param("id") long id);

    @Select("""
            SELECT b.tracker_id, b.product_space_id, b.classification_id, b.sharing_mode, b.origin
            FROM tracker_bindings b
            JOIN trackers t ON t.tracker_id = b.tracker_id
            WHERE b.working_set_id = #{id} AND t.visible
            ORDER BY b.product_space_id, b.classification_id
            """)
    List<TrackerBindingSummary> findTrackerBindings(@Param("id") long id);

    @Select("""
            SELECT 1 FROM tracker_bindings
            WHERE working_set_id = #{workingSetId} AND tracker_id = #{trackerId}
            """)
    Integer findTrackerBinding(@Param("workingSetId") long workingSetId, @Param("trackerId") long trackerId);
}
