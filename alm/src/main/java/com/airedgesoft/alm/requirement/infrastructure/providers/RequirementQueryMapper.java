package com.airedgesoft.alm.requirement.infrastructure.providers;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Requirement 查询 Mapper；仅包含 SELECT。
 */
@Mapper
public interface RequirementQueryMapper {

    @Select("""
            SELECT instance_id AS local_id, common_id, tracker AS tracker_id, number, name AS title,
                   description, revision, version, lock_version, (delete_flag = 1) AS deleted, TRUE AS materialized, (checked_out = 1) AS checked_out, flags
            FROM air_inst_requirement_rev WHERE instance_id = #{id} AND revision = #{revision}
            """)
    RequirementDetail findRevision(@Param("id") long id, @Param("revision") int revision);

    @Select("""
            SELECT local_id, common_id, tracker_id, number, title, description, revision, version,
                   lock_version, delete_flag AS deleted, TRUE AS materialized, (checked_out = 1) AS checked_out, flags
            FROM requirements WHERE local_id = #{id}
            """)
    RequirementDetail find(@Param("id") long id);

    @Select("""
            SELECT local_id, common_id, tracker_id, number, title, description, revision, version,
                   lock_version, delete_flag AS deleted, TRUE AS materialized, (checked_out = 1) AS checked_out, flags
            FROM requirements
            WHERE tracker_id = #{trackerId}
            ORDER BY local_id
            """)
    List<RequirementDetail> findByTracker(@Param("trackerId") long trackerId);

    @Select("""
            <script>
            SELECT local_id, common_id, tracker_id, number, title, description, revision, version,
                   lock_version, delete_flag AS deleted, TRUE AS materialized, (checked_out = 1) AS checked_out, flags
            FROM requirements
            WHERE local_id IN
            <foreach collection="localIds" item="localId" open="(" separator="," close=")">
                #{localId}
            </foreach>
            </script>
            """)
    List<RequirementDetail> findByIds(@Param("localIds") List<Long> localIds);
}
