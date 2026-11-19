package sk.workingset.infrastructure.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** Physical row of {@code air_inst_workingset}. */
public record WorkingSetRow(
        Long id,
        Long baseClass,
        Long classId,
        Long type,
        String number,
        String name,
        String description,
        String color,
        Long workingSetId,
        Long workSpace,
        Long owner,
        Long instanceId,
        Long commonId,
        Long parentId,
        Integer deleteFlag,
        String flags,
        String attributes,
        Long revision,
        Long versionType,
        Long lockVersion,
        String createdBy,
        Long createdTime,
        String updatedBy,
        Long updatedTime,
        Long tracker,
        Long workingSetScope,
        Map<String, Object> customFields
) {
    private static final WorkingSetCustomFieldMapper CUSTOM_FIELDS = new WorkingSetCustomFieldMapper();

    static WorkingSetRow read(ResultSet rs) throws SQLException {
        return new WorkingSetRow(
                rs.getObject("id", Long.class),
                rs.getObject("base_class", Long.class),
                rs.getObject("class", Long.class),
                rs.getObject("type", Long.class),
                rs.getString("number"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("color"),
                rs.getObject("workingset_id", Long.class),
                rs.getObject("work_space", Long.class),
                rs.getObject("owner", Long.class),
                rs.getObject("instance_id", Long.class),
                rs.getObject("common_id", Long.class),
                rs.getObject("parent_id", Long.class),
                rs.getObject("delete_flag", Integer.class),
                rs.getString("flags"),
                rs.getString("attributes"),
                rs.getObject("revision", Long.class),
                rs.getObject("version_type", Long.class),
                rs.getObject("lock_version", Long.class),
                rs.getString("created_by"),
                rs.getObject("created_time", Long.class),
                rs.getString("updated_by"),
                rs.getObject("updated_time", Long.class),
                rs.getObject("tracker", Long.class),
                rs.getObject("workingset_scop", Long.class),
                CUSTOM_FIELDS.read(rs));
    }
}
