package sk.workingset.infrastructure.providers.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import sk.workingset.application.query.model.*;

import java.util.List;

/**
 * Working Set 查询 Mapper；仅包含 SELECT。
 */
@Mapper
public interface WorkingSetQueryMapper {

    @Select("""
            SELECT id AS working_set_id, NULLIF(parent_id, 0) AS parent_working_set_id, name,
                   (id = 1) AS default_working_set, COALESCE(color, '#FFFF') AS color
            FROM air_inst_workingset WHERE id = #{id} AND delete_flag = 0
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
            SELECT w.id FROM air_inst_workingset w
            WHERE w.parent_id = #{id} AND w.delete_flag = 0
              AND NOT EXISTS (
                  SELECT 1 FROM working_set_creations c
                  WHERE c.new_working_set_id = w.id
                    AND c.status IN ('Pending', 'Running')
                    AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
            ORDER BY w.id
            """)
    List<Long> findChildren(@Param("id") long id);

    @Select("""
            WITH RECURSIVE lineage(working_set_id, parent_working_set_id, distance) AS (
                SELECT id, NULLIF(parent_id, 0), 0
                FROM air_inst_workingset WHERE id = #{id} AND delete_flag = 0
                UNION ALL
                SELECT p.id, NULLIF(p.parent_id, 0), c.distance + 1
                FROM air_inst_workingset p JOIN lineage c ON p.id = c.parent_working_set_id
                WHERE p.delete_flag = 0
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
            SELECT t.id AS tracker_id, t.name, NULLIF(t.parent_id, 0) AS parent_id,
                   t.source_branch_id AS source_id, t.work_space AS product_space_id,
                   t.tracker_class AS classification_id,
                   CASE t.share WHEN 1 THEN 'SHARED' ELSE 'UNSHARED' END AS sharing_mode,
                   t.is_default = 1 AS is_default
            FROM air_inst_tracker_rev t
            WHERE t.delete_flag = 0 AND t.visible = 1
              AND t.work_space = COALESCE(#{workspace,jdbcType=BIGINT}, t.work_space)
              AND (t.workingset_id = #{id} OR EXISTS (
                SELECT 1 FROM tracker_bindings b
                WHERE b.working_set_id = #{id} AND b.tracker_id = t.id))
              AND NOT EXISTS (SELECT 1 FROM tracker_bindings b
                WHERE b.working_set_id = #{id} AND b.tracker_id = t.id AND NOT b.shown)
            ORDER BY t.id
            """)
    List<TrackerSummary> findTrackers(@Param("id") long id, @Param("workspace") Long workspace);

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
              AND sb.product_space_id = COALESCE(#{workspace,jdbcType=BIGINT}, sb.product_space_id)
            ORDER BY sb.product_space_id
            """)
    List<SpaceBindingSummary> findSpaceBindings(@Param("id") long id, @Param("workspace") Long workspace);

    @Select("""
            SELECT b.tracker_id, b.product_space_id, b.classification_id, b.sharing_mode, b.origin, b.shown,
                   (t.tracker_class = 10070101 AND NULLIF(t.parent_id, 0) IS NULL AND t.share = 0) AS root_folder
            FROM tracker_bindings b
            JOIN air_inst_tracker_rev t ON t.id = b.tracker_id AND t.work_space = b.product_space_id
            WHERE b.working_set_id = #{id} AND t.delete_flag = 0 AND t.visible = 1
              AND t.work_space = COALESCE(#{workspace,jdbcType=BIGINT}, t.work_space)
            ORDER BY b.product_space_id, b.classification_id
            """)
    List<TrackerBindingSummary> findTrackerBindings(@Param("id") long id, @Param("workspace") Long workspace);

    @Select("SELECT working_set_id FROM space_bindings WHERE product_space_id = #{spaceId} ORDER BY working_set_id")
    List<Long> findWorkingSetsBoundToSpace(@Param("spaceId") long spaceId);

    @Select("SELECT DISTINCT working_set_id FROM tracker_bindings WHERE tracker_id = #{trackerId} ORDER BY working_set_id")
    List<Long> findWorkingSetIdsBoundToTracker(@Param("trackerId") long trackerId);

}
