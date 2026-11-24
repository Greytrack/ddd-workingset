//package com.airedgesoft.bridge.v3.metadata.cache;
//
//import com.airedgesoft.bridge.v3.classification.model.ClassSnapshot;
//
//import java.util.*;
//
///**
// * 元数据端口实现：把 V3 的 {@code AdminCache} / {@code IInnerAdmin} 类型分发入口按语义拆成强类型方法。
// *
// * <p>
// * 所有返回值都是 V4 扁平快照，V3 类型不出本类。缓存单例由 V3 侧维护，本类只读。
// * </p>
// */
//class V3MetadataCacheAdapter implements MetadataCachePort {
//
//
//    public List<ClassSnapshot> baseClasses() {
//        return Collections.emptyList();
//    }
//
//    public List<Long> allClassIds() {
//        return Collections.emptyList();
//    }
//
//    public List<ClassSnapshot> allBaseClasses() {
//        return Collections.emptyList();
//    }
//
//    public List<ClassSnapshot> allSubClasses() {
//        return Collections.emptyList();
//    }
//
//    public NodeSnapshot rootPrivilegeNode() {
//        return null;
//    }
//
//    public NodeSnapshot node(Long nodeId) {
//        return null;
//    }
//
//    public void refreshNodeCache(Collection<Long> nodeIds) {
//
//    }
//
//    public ClassSnapshot classSnapshot(Long classId) {
//        return null;
//    }
//
//    public Set<String> tableColumns(String tableName) {
//        return Collections.emptySet();
//    }
//
//    public Long tableInherit(Long tableId) {
//        return 0L;
//    }
//
//    public String nodeApiId(Long nodeId) {
//        return "";
//    }
//
//    public List<Long> enabledPrivilegeIdsByRoleIds(Collection<Long> roleIds) {
//        return Collections.emptyList();
//    }
//
//    public Set<Long> expandClassIds(Long classId) {
//        return Collections.emptySet();
//    }
//
//    public Map<Long, List<Long>> appliedToMapByPrivilegeIds(Collection<Long> privilegeIds) {
//        return Collections.emptyMap();
//    }
//}
