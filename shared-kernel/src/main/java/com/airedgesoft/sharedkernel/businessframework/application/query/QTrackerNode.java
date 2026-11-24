package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 产品空间分类 Tracker 树节点（向导树/左树共用投影：平铺集合，按 parentId 组树，§13 禁嵌套树 JSON）。
 *
 * <p>
 * 计算型只读投影（非资源）：intent=WORKINGSET_CREATE / WORKINGSET_EXTEND 附加可加入性与分支信息；左树附加计数 / 共享 / 权限打码 / 新建预判。
 *
 * @param name
 *            AIP-122 资源名（相对形态，如 trackers/623809242866016）
 * @param id
 *            Tracker id（十进制字符串）
 * @param displayName
 *            Tracker 展示名
 * @param parentId
 *            父节点 id（分类树层级；根为 null）
 * @param instanceCount
 *            节点下实例数（左树计数展示）
 * @param isShared
 *            是否共享节点
 * @param canView
 *            当前用户可查看（false = 打码，仅发现权限过滤）
 * @param canCreateTracker
 *            当前用户可在此节点下新建 Tracker（左树新建预判）
 * @param defaultViewType
 *            默认视图类型（表格 / 文档 / 卡片）
 * @param canAdd
 *            可加入当前操作的工作集（可加入性：向导第 2 步 / 拓展 / 添加至工作集共用）
 * @param branchType
 *            已加入时的分支类型：SHARED（共享）/ INCLUDED（纳入；分支信息）
 */
@Schema(description = "产品空间分类 Tracker 树节点（平铺投影，非资源）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QTrackerNode(@Schema(description = "AIP-122 资源名（相对形态，如 trackers/623809242866016）") String name,
        @Schema(description = "Tracker id（十进制字符串）") String id, @Schema(description = "Tracker 展示名") String displayName,
        @Schema(description = "父节点 id（根为 null）") String parentId, @Schema(description = "节点下实例数") Integer instanceCount,
        @Schema(description = "是否共享节点") Boolean isShared, @Schema(description = "当前用户可查看（false = 打码）") Boolean canView,
        @Schema(description = "当前用户可在此节点下新建 Tracker") Boolean canCreateTracker,
        @Schema(description = "默认视图类型（表格/文档/卡片）") String defaultViewType,
        @Schema(description = "可加入当前操作的工作集（可加入性）") Boolean canAdd,
        @Schema(description = "已加入时的分支类型：SHARED / INCLUDED") String branchType) {
}
