package sk.tracker.infrastructure.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Physical row of {@code air_ws_tracker}. */
public record TrackerCompatibilityRow(
        Long id,
        Long parentId,
        String number,
        String name,
        String description,
        String color,
        Long workingSetId,
        Long subClassId,
        Long sourceBranchId,
        Short visible,
        Short locked,
        Integer shared,
        Short isDefault,
        String attributes,
        Integer deleteFlag,
        Long lockVersion,
        String createdBy,
        Long createdTime,
        String updatedBy,
        Long updatedTime
) {
    static TrackerCompatibilityRow read(ResultSet rs) throws SQLException {
        return new TrackerCompatibilityRow(
                rs.getObject("id", Long.class),
                rs.getObject("parent_id", Long.class),
                rs.getString("number"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("color"),
                rs.getObject("workingset_id", Long.class),
                rs.getObject("sub_class_id", Long.class),
                rs.getObject("source_branch_id", Long.class),
                rs.getObject("visible", Short.class),
                rs.getObject("locked", Short.class),
                rs.getObject("shared", Integer.class),
                rs.getObject("is_default", Short.class),
                rs.getString("attributes"),
                rs.getObject("delete_flag", Integer.class),
                rs.getObject("lock_version", Long.class),
                rs.getString("created_by"),
                rs.getObject("created_time", Long.class),
                rs.getString("updated_by"),
                rs.getObject("updated_time", Long.class));
    }
}
