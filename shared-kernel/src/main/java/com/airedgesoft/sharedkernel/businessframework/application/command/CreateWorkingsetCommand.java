package com.airedgesoft.sharedkernel.businessframework.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 创建工作集命令（新建向导：两步——基本信息 + 空间×Tracker 选择）。
 *
 * @param displayName
 *            工作集展示名（必填；重名 409）
 * @param owner
 *            所有者 id（必填）
 * @param description
 *            描述
 * @param color
 *            颜色标记
 * @param productSpaces
 *            挂接的产品空间 id 集合（不存在 404）
 * @param branchType
 *            分支类型：SHARED（共享）/ INCLUDED（纳入）
 * @param baselineRef
 *            基线引用：head 或基线 id（head = 跟随最新）
 */
@Schema(description = "创建工作集命令（幂等键必接）")
public record CreateWorkingsetCommand(@Schema(description = "工作集展示名（必填；重名 409）") String displayName,
        @Schema(description = "所有者 id（必填，十进制字符串）") String owner, @Schema(description = "描述") String description,
        @Schema(description = "颜色标记") String color,
        @Schema(description = "挂接的产品空间 id 集合（不存在 404）") List<String> productSpaces,
        @Schema(description = "分支类型：SHARED（共享）/ INCLUDED（纳入）") String branchType,
        @Schema(description = "基线引用：head 或基线 id") String baselineRef) {
}
