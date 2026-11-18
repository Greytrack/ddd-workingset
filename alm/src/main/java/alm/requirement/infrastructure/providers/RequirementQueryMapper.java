package alm.requirement.infrastructure.providers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import alm.requirement.application.query.model.RequirementDetail;

/** Requirement 查询 Mapper；仅包含 SELECT。 */
@Mapper
public interface RequirementQueryMapper {

    @Select("""
            SELECT local_id, common_id, tracker_id, number, title, description, revision, version,
                   lock_version, delete_flag AS deleted, TRUE AS materialized
            FROM requirements WHERE local_id = #{id}
            """)
    RequirementDetail find(@Param("id") long id);

    @Select("""
            <script>
            SELECT local_id, common_id, tracker_id, number, title, description, revision, version,
                   lock_version, delete_flag AS deleted, TRUE AS materialized
            FROM requirements
            WHERE tracker_id = #{trackerId} AND NOT delete_flag
            <if test="workingSetId != null">AND working_set_id = #{workingSetId}</if>
            ORDER BY local_id
            </script>
            """)
    List<RequirementDetail> findByTracker(@Param("trackerId") long trackerId,
                                          @Param("workingSetId") Long workingSetId);
}
