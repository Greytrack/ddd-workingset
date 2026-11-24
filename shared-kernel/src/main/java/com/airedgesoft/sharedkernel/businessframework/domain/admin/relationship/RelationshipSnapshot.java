package com.airedgesoft.sharedkernel.businessframework.domain.admin.relationship;

import lombok.Data;

/**
 * 关系快照（替代 v3 {@code SysRelationship}）：判权只读「关系类型 + 父（起始）ID + 值」三列。
 *
 * <p>
 * 端口不暴露 v3 DTO，因此这里定义判权所需的最小字段面；装配时由 {@code v4-bridge-v3} 投影。
 * </p>
 */
@Data
public class RelationshipSnapshot {

    /** 父（起始）节点 ID */
    private Long parentId;

    /** 关系类型 */
    private Integer relationType;

    /** 目标值 ID */
    private Long value;

    /** 辅助 ID（部分关系类型携带） */
    private Long helpId;

    /** 辅助字符串（部分关系类型携带） */
    private String helpStr;
}
