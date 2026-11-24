package com.airedgesoft.sharedkernel.businessframework.application.command;

/**
 * Tracker 拖拽排序命令（左树拖拽；position = 目标锚点相对位置）。
 *
 * @param targetTrackerId
 *            目标锚点 Tracker id（拖到自身 409；跨分类 409）
 * @param position
 *            相对位置：BEFORE（前）/ AFTER（后）
 */
public record TrackerMoveCommand(String targetTrackerId, String position) {
}
