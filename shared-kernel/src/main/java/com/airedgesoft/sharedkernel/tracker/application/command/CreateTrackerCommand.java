package com.airedgesoft.sharedkernel.tracker.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建 Tracker 命令（左树新建，hover「+」；仅默认工作集放开——非默认工作集 409，权限归 _access）。
 *
 * @param trackerClassId
 *            Tracker 分类 id（分类不存在 404）
 * @param workSpaceId
 *            所属产品空间 id
 * @param workingsetId
 *            工作集 id（仅默认工作集可建；非默认 409）
 * @param displayName
 *            Tracker 展示名
 * @param parentId
 *            父节点 id（树挂载；可空 = 顶层）
 */
@Schema(description = "创建 Tracker 命令（仅默认工作集放开；幂等键必接）")
public record CreateTrackerCommand(@Schema(description = "Tracker 分类 id（不存在 404）") String trackerClassId,
        @Schema(description = "所属产品空间 id") String workSpaceId,
        @Schema(description = "工作集 id（仅默认工作集可建，非默认 409）") String workingsetId,
        @Schema(description = "Tracker 展示名") String displayName,
        @Schema(description = "父节点 id（可空 = 顶层）") String parentId) {
}
