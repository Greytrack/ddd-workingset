package com.airedgesoft.sharedkernel.businessframework.domain.admin.criteria.definition;

import lombok.Data;

import java.util.List;

/** 数据条件快照（替代 v3 的 {@code AdminCriteria} 的读取面；条件求值本身留在 v3，见 CriteriaPort）。 */
@Data
public class CriteriaSnapshot {

    /** 条件 ID */
    private Long id;

    /** 条件所属分类 ID（v3 AdminCriteria#getClassId） */
    private Long classId;

    /** 条件名称 */
    private String name;

    /** 条件引用的属性 ID 列表 */
    private List<Long> attrIds;

    /** 条件引用的子级穿透属性 ID 列表 */
    private List<Long> subAttrIds;

    /** 条件引用的页签 ID 列表（与 attrIds 同序；v3 CriteriaRowInfo.getTabIdList 语义） */
    private List<Long> tabIds;

    /** 条件所属主表名（v3 CriteriaSnapshot.getQueryBuilder().getMainTableName() 的判权子集） */
    private String mainTableName;

    /** v3 兼容面：自返（{@code ReadAndModifyPrivilegeCoreService} 判空用） */
    public CriteriaSnapshot getCriteriaRowInfo() {
        return this;
    }

    /** v3 兼容面：页签 ID 列表 */
    public List<Long> getTabIdList() {
        return tabIds == null ? List.of() : tabIds;
    }

    /** v3 兼容面：属性 ID 列表 */
    public List<Long> getAttrIdList() {
        return attrIds == null ? List.of() : attrIds;
    }

    /** v3 兼容面：getQueryBuilder().getMainTableName() */
    public String getMainTableName() {
        return mainTableName;
    }
}
