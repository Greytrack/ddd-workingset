package com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute.AttributeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.criteria.definition.CriteriaSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata.model.NodeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.privilege.PrivilegeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.productspace.ProductSpaceSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.relationship.RelationshipSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.role.RoleSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.table.TableSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.tracker.TrackerPermissionSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权元数据端口：判权过程所需的分类 / 页签 / 属性 / 权限 / 产品空间等配置数据的只读入口。
 *
 * <p>
 * 存在原因：v3 的 {@code AdminCache.getNodeById()} 是「类型分发中心」（同一方法按调用方强转成 8 种类型），无法 1:1 包一层； 这里按语义拆成强类型方法，返回扁平快照。
 *
 * <p>
 * 实现位置：{@code v4-bridge-v3}（只有它可以直连 v3 实现），v4 侧只依赖本接口。
 */
public interface MetadataCachePort {

    /** 全部基础类（v3 {@code IInnerAdmin#getAirClasses(ALL_BASECLASS)}） */
    List<ClassSnapshot> baseClasses();

    /** 全部分类 ID（v3 {@code IInnerAdmin#getAirClasses(AdminEnum.ALL)} 的判权子集） */
    List<Long> allClassIds();

    /** 全部基类（v3 {@code IInnerAdmin#getAirClasses(AdminEnum.ALL_BASECLASS)}） */
    List<ClassSnapshot> allBaseClasses();

    /** 全部子类（v3 {@code IInnerAdmin#getAirClasses(AdminEnum.ALL_SUBCLASS)}） */
    List<ClassSnapshot> allSubClasses();

    /** 角色快照；不存在返回 {@code null} */
    RoleSnapshot roleSnapshot(Long roleId);

    /** 权限树根节点（v3 {@code getNodeById(INodeId.ROOT_PRIVILEGE_ID)}） */
    NodeSnapshot rootPrivilegeNode();

    /** 元数据节点快照（任意节点类型）；不存在返回 {@code null} */
    NodeSnapshot node(Long nodeId);

    /** 刷新指定节点缓存（v3 {@code IAdminNode#refreshNodeCaches(List)}） */
    void refreshNodeCache(Collection<Long> nodeIds);

    /** 分类快照；不存在返回 {@code null} */
    ClassSnapshot classSnapshot(Long classId);

    /** 页签快照；不存在返回 {@code null} */
    TableSnapshot tableSnapshot(Long tableId);

    /** 属性快照；不存在返回 {@code null} */
    AttributeSnapshot attributeSnapshot(Long attributeId);

    /** 权限配置快照；不存在返回 {@code null} */
    PrivilegeSnapshot privilegeSnapshot(Long privilegeId);

    /** 数据条件快照；不存在返回 {@code null} */
    CriteriaSnapshot criteriaSnapshot(Long criteriaId);

    /** 物理表列名集合（v3 {@code TableMetadataCachePool#getTableColumns}） */
    Set<String> tableColumns(String tableName);

    /** 页签的继承来源页签 ID（v3 {@code BaseNode#getInherit()}）；不存在返回 {@code null} */
    Long tableInherit(Long tableId);

    /** 任意节点（分类/属性/页签/权限）的 API ID；不存在返回 {@code null} */
    String nodeApiId(Long nodeId);

    /** 角色下「角色与权限都启用」的权限 ID（v3 {@code PrivilegeNode#isEnabled()} 语义） */
    List<Long> enabledPrivilegeIdsByRoleIds(Collection<Long> roleIds);

    /** 角色对应的权限配置快照列表 */
    List<PrivilegeSnapshot> privilegesByRoleIds(Collection<Long> roleIds);

    /** 分类展开：返回该分类及其关联的中类/子类/父类/基础类 ID 集合（等价 v3 Admin.getAllClass/getAllSubClass 逻辑） */
    Set<Long> expandClassIds(Long classId);

    /** 产品空间快照；不存在返回 {@code null} */
    ProductSpaceSnapshot productSpace(Long instanceId);

    /** 权限 ID → 生效范围（appliedTo）ID 列表 */
    Map<Long, List<Long>> appliedToMapByPrivilegeIds(Collection<Long> privilegeIds);

    /** 分支成员权限列表 */
    List<TrackerPermissionSnapshot> trackerPermissions(Long trackerId);

    /** 按关系类型 + 值集合查询关系 */
    List<RelationshipSnapshot> relationshipsByTypeAndValues(Integer relationType, List<Long> values);

    /** 按关系类型集合 + 父（起始）值集合查询关系 */
    List<RelationshipSnapshot> relationshipsByTypesAndParents(List<Integer> relationTypes, List<Long> parentValues);
}
