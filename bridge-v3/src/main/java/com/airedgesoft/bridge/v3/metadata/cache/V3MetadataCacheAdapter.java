package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute.AttributeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.criteria.definition.CriteriaSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata.MetadataCachePort;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata.model.NodeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.privilege.PrivilegeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.productspace.ProductSpaceSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.relationship.RelationshipSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.role.RoleSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.table.TableSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.tracker.TrackerPermissionSnapshot;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class V3MetadataCacheAdapter implements MetadataCachePort {

    /**
     * 1通过本地数据库查询
     */
    @Resource
    private JdbcTemplate jdbc;

    private static final RowMapper<ClassSnapshot> CLASSIFICATION = (row, position) ->
            ClassSnapshot.build(row.getLong(1),
                    row.getBoolean(2),
                    row.getString(3),
                    row.getLong(4),
                    row.getLong(5));

    private static final String SELECT = "SELECT classification_id, sharing_mode = 'SHARED' AS shared, name, base_class_id, class_id FROM classifications";

    @Override
    public List<ClassSnapshot> baseClasses() {
        return List.of();
    }

    @Override
    public List<Long> allClassIds() {
        return List.of();
    }

    @Override
    public List<ClassSnapshot> allBaseClasses() {
        return List.of();
    }

    @Override
    public List<ClassSnapshot> allSubClasses() {
        return jdbc.query(SELECT + " ORDER BY classification_id",
                CLASSIFICATION);
    }

    @Override
    public RoleSnapshot roleSnapshot(Long roleId) {
        return null;
    }

    @Override
    public NodeSnapshot rootPrivilegeNode() {
        return null;
    }

    @Override
    public NodeSnapshot node(Long nodeId) {
        return null;
    }

    @Override
    public void refreshNodeCache(Collection<Long> nodeIds) {

    }

    @Override
    public ClassSnapshot classSnapshot(Long classId) {
        return jdbc.query(SELECT + " WHERE classification_id = ?",
                CLASSIFICATION, classId).stream().findFirst().orElse(null);
    }

    @Override
    public TableSnapshot tableSnapshot(Long tableId) {
        return null;
    }

    @Override
    public AttributeSnapshot attributeSnapshot(Long attributeId) {
        return null;
    }

    @Override
    public PrivilegeSnapshot privilegeSnapshot(Long privilegeId) {
        return null;
    }

    @Override
    public CriteriaSnapshot criteriaSnapshot(Long criteriaId) {
        return null;
    }

    @Override
    public Set<String> tableColumns(String tableName) {
        return Set.of();
    }

    @Override
    public Long tableInherit(Long tableId) {
        return 0L;
    }

    @Override
    public String nodeApiId(Long nodeId) {
        return "";
    }

    @Override
    public List<Long> enabledPrivilegeIdsByRoleIds(Collection<Long> roleIds) {
        return List.of();
    }

    @Override
    public List<PrivilegeSnapshot> privilegesByRoleIds(Collection<Long> roleIds) {
        return List.of();
    }

    @Override
    public Set<Long> expandClassIds(Long classId) {
        return Set.of();
    }

    @Override
    public ProductSpaceSnapshot productSpace(Long instanceId) {
        return null;
    }

    @Override
    public Map<Long, List<Long>> appliedToMapByPrivilegeIds(Collection<Long> privilegeIds) {
        return Map.of();
    }

    @Override
    public List<TrackerPermissionSnapshot> trackerPermissions(Long trackerId) {
        return List.of();
    }

    @Override
    public List<RelationshipSnapshot> relationshipsByTypeAndValues(Integer relationType, List<Long> values) {
        return List.of();
    }

    @Override
    public List<RelationshipSnapshot> relationshipsByTypesAndParents(List<Integer> relationTypes, List<Long> parentValues) {
        return List.of();
    }
}
