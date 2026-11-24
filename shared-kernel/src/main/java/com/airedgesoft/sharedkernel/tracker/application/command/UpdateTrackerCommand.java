package com.airedgesoft.sharedkernel.tracker.application.command;

/**
 * 更新 Tracker 命令（配置编辑；掩码白名单 = displayName / instanceTreeShowContent，规范 §3）。
 *
 * @param displayName
 *            Tracker 展示名
 * @param instanceTreeShowContent
 *            树节点显示内容配置（§15 分类侧配置读侧，legacy updateTracker 实证）
 */
public record UpdateTrackerCommand(String displayName, String instanceTreeShowContent) {
}
