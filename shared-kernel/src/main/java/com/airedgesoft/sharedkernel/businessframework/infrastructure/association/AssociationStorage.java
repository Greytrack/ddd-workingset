package com.airedgesoft.sharedkernel.businessframework.infrastructure.association;

import java.util.List;

/**
 * 关联表列名常量（{@code air_inst_tab_association}；与 v3 同列）——基础设施 SSOT，领域层不见列名。
 *
 * <p>
 * <b>v3 产物考古（2026-11-23，只读核对 {@code IInsertSql#getInsertTableSql} 单版次插入路径）</b>：v3 关联行只写系统列
 * （id/审计/lock_version/两端身份/归属/`tab_id`）与业务属性列（relationship_type/propagation_type/changed/merge 基线/可选属性）；
 * {@code revision_in/revision_out/flags/bit_flags/edit_row/current_head/related_revision_id/attributes/pending_investigation/test_run}
 * 在该路径下不写入（保持 NULL）。v4 按同一口径写，读侧宽容。
 */
final class AssociationStorage {

    static final String TABLE = "air_inst_tab_association";

    static final String ID = "id";
    static final String COMMON_ID = "common_id";
    static final String INSTANCE_ID = "instance_id";
    static final String INSTANCE_TYPE = "instance_type";
    static final String DOWNSTREAM_MERGE_REVISION = "downstream_merge_revision";
    static final String DOWNSTREAM_CHANGED = "downstream_changed";
    static final String RELATED_INSTANCE_ID = "related_instance_id";
    static final String RELATED_COMMON_ID = "related_common_id";
    static final String RELATED_TYPE = "related_type";
    static final String RELATED_WORKINGSET_ID = "related_workingset_id";
    static final String UPSTREAM_CHANGED = "upstream_changed";
    static final String UPSTREAM_MERGE_REVISION = "upstream_merge_revision";
    static final String PROPAGATION_TYPE = "propagation_type";
    static final String RELATIONSHIP_TYPE = "relationship_type";
    static final String TAB_ID = "tab_id";
    static final String LOCK_VERSION = "lock_version";
    static final String CREATED_BY = "created_by";
    static final String CREATED_TIME = "created_time";
    static final String UPDATED_BY = "updated_by";
    static final String UPDATED_TIME = "updated_time";
    static final String WORK_SPACE = "work_space";
    static final String RELATED_WORK_SPACE = "related_work_space";
    static final String WORKINGSET_ID = "workingset_id";
    static final String TRACKER = "tracker";
    static final String RELATED_TRACKER = "related_tracker";
    static final String EDIT_RULE = "edit_rule";
    static final String EDIT_RULE_NAME = "edit_rule_name";
    static final String DIRECTION = "direction";

    /** v3 单版次关联行固定方向位（从宿主侧创建的行；{@code AssociationTable#createRows} 以 direction==2 判「已挂载」）。 */
    static final int DIRECTION_HOST_SIDE = 2;

    /** 规则三表。 */
    static final String RULE_TABLE = "air_inst_relation_rule";
    static final String RULE_FIELD_VALUE_TABLE = "air_inst_relation_rule_field_value";
    static final String QUERY_CRITERIA_TABLE = "air_inst_query_criteria";

    static final String RULE_ID = "id";
    static final String RULE_RELATIONSHIP_ID = "relationship_id";
    static final String RULE_INSTANCE_ID = "instance_id";
    static final String RULE_INSTANCE_TYPE = "instance_type";
    static final String RULE_INSTANCE_NUMBER = "instance_number";
    static final String RULE_RELATED_INSTANCE_ID = "related_instance_id";
    static final String RULE_RELATED_TYPE = "related_type";
    static final String RULE_RELATED_NUMBER = "related_number";
    static final String RULE_DRIVE_TYPE = "drive_type";
    static final String RULE_EXECUTED = "executed";
    static final String RULE_EDIT_RULE_NAME = "edit_rule_name";

    static final String CRITERIA_QUERY_ID = "query_id";
    static final String CRITERIA_ATTR_ID = "attr_id";
    static final String CRITERIA_VALUE = "value";

    static final String FIELD_VALUE_RELATION_RULE_ID = "relation_rule_id";
    static final String FIELD_VALUE_ATTR_ID = "attr_id";
    static final String FIELD_VALUE_VALUE = "value";

    /** 可选属性列（v3 预置类型化 01–05 段；顺序即写库顺序）。 */
    static final List<String> OPTIONAL_COLUMNS = List.of(
            // 日期
            "date01", "date02", "date03", "date04", "date05",
            // 区间
            "startdate01", "endstartdate01", "startdate02", "endstartdate02", "startdate03", "endstartdate03",
            "startdate04", "endstartdate04", "startdate05", "endstartdate05",
            // 文本
            "text01", "text02", "text03",
            // 长文本
            "largetext01", "largetext02", "largetext03", "largetext04", "largetext05",
            // 组合文本
            "compositetext01", "compositetext02", "compositetext03", "compositetext04", "compositetext05",
            // 富文本
            "richtext01", "richtext02", "richtext03", "richtext04", "richtext05",
            // 列表 / 多列表
            "list01", "list02", "list03", "list04", "list05", "multilist01", "multilist02", "multilist03",
            "multilist04", "multilist05",
            // 数值 / 组合数值
            "numeric01", "numeric02", "numeric03", "numeric04", "numeric05", "compositenumber01", "compositenumber02",
            "compositenumber03", "compositenumber04", "compositenumber05");

    private AssociationStorage() {
    }
}
