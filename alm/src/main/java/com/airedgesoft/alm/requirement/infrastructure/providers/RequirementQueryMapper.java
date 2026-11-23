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

    String SELECT_REVISION = """
            SELECT r.instance_id AS local_id, r.common_id, r.tracker AS tracker_id, r.number, r.name AS title,
                   r.description, r.revision, r.version, r.lock_version, (r.delete_flag = 1) AS deleted,
                   TRUE AS materialized, (r.checked_out = 1) AS checked_out, r.flags,
                   r.created_by, r.created_time, r.updated_by, r.updated_time
            FROM air_inst_requirement_rev r
            """;

    String LATEST = """
            AND NOT EXISTS (
                SELECT 1 FROM air_inst_requirement_rev newer
                WHERE newer.instance_id = r.instance_id AND newer.revision > r.revision
            )
            """;

    @Select(SELECT_REVISION + "WHERE r.instance_id = #{id} AND r.revision = #{revision}")
    RequirementDetail findRevision(@Param("id") long id, @Param("revision") int revision);

    @Select(SELECT_REVISION + "WHERE r.instance_id = #{id} ORDER BY r.revision DESC LIMIT 1")
    RequirementDetail find(@Param("id") long id);

    @Select(SELECT_REVISION + "WHERE r.tracker = #{trackerId} " + LATEST + "ORDER BY r.instance_id")
    List<RequirementDetail> findByTracker(@Param("trackerId") long trackerId);

    @Select("<script>" + SELECT_REVISION + """
            WHERE r.instance_id IN
            <foreach collection="localIds" item="localId" open="(" separator="," close=")">
                #{localId}
            </foreach>
            """ + LATEST + "</script>")
    List<RequirementDetail> findByIds(@Param("localIds") List<Long> localIds);
}
