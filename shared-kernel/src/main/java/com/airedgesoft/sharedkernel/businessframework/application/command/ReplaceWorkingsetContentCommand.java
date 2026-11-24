package com.airedgesoft.sharedkernel.businessframework.application.command;

/**
 * 工作集内容替换命令（GateFlow 回滚场景，legacy createRollbackWorkingset 异步 Job 实证；LRO 承载）。
 *
 * @param sourceWorkingsetId
 *            源工作集 id（其内容替换本工作集）
 */
public record ReplaceWorkingsetContentCommand(String sourceWorkingsetId) {
}
