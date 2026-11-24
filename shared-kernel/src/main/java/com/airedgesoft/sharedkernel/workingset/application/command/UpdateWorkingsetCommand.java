package com.airedgesoft.sharedkernel.workingset.application.command;

/**
 * 更新工作集命令（编辑基本信息；掩码白名单 = displayName / owner / description / color，规范 §3）。
 *
 * @param displayName
 *            工作集展示名
 * @param owner
 *            所有者 id
 * @param description
 *            描述
 * @param color
 *            颜色标记
 */
public record UpdateWorkingsetCommand(String displayName, String owner, String description, String color) {
}
