package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 工作集读模型（v4 契约，规范 §1/§3）：列表 / 详情 / 写端点出参共用同一契约。
 *
 * <p>
 * 技术主键契约形态为十进制字符串（规范 §2）；时间点为 RFC 3339 字符串（规范 §2.1）。
 *
 * @param name
 *            AIP-122 资源名（相对形态，如 workingsets/623809242866016）
 * @param id
 *            工作集聚合 id（v4 雪花 ID 十进制字符串）
 * @param displayName
 *            工作集展示名（AIP-148）
 * @param owner
 *            所有者 id（跨 BC 用户引用，§16）
 * @param ownerDisplayName
 *            所有者只读展示名（§16）
 * @param description
 *            描述
 * @param color
 *            颜色标记（顶栏选择器视觉区分）
 * @param parentId
 *            父工作集 id（平铺集合按此组装树，§13 禁嵌套树 JSON；根节点为 null）
 * @param canView
 *            当前用户可查看（false = 选择器中禁选打码）
 * @param branchType
 *            分支类型：SHARED（共享）/ INCLUDED（纳入）
 * @param baselineRef
 *            基线引用：head 或基线 id
 * @param productSpaceIds
 *            挂接的产品空间 id 集合
 * @param createTime
 *            创建时刻（RFC 3339，output-only）
 * @param updateTime
 *            最近更新时刻（RFC 3339，output-only）
 */
@Schema(description = "工作集资源（平铺集合节点；树形由 parentId 组装，§13）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QWorkingset(@Schema(description = "AIP-122 资源名（相对形态，如 workingsets/623809242866016）") String name,
        @Schema(description = "工作集聚合 id（十进制字符串）") String id, @Schema(description = "工作集展示名") String displayName,
        @Schema(description = "所有者 id（十进制字符串）") String owner, @Schema(description = "所有者只读展示名") String ownerDisplayName,
        @Schema(description = "描述") String description, @Schema(description = "颜色标记") String color,
        @Schema(description = "父工作集 id（根节点为 null）") String parentId,
        @Schema(description = "当前用户可查看（false = 选择器禁选打码）") Boolean canView,
        @Schema(description = "分支类型：SHARED（共享）/ INCLUDED（纳入）") String branchType,
        @Schema(description = "基线引用：head 或基线 id") String baselineRef,
        @Schema(description = "挂接的产品空间 id 集合") List<String> productSpaceIds,
        @Schema(description = "创建时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String createTime,
        @Schema(description = "最近更新时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String updateTime) {
}
