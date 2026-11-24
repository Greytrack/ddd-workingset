package com.airedgesoft.sharedkernel.tracker.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 删除 Tracker 命令（软删入回收站，legacy sendTrackerToRecycleBin 实证；占用 409）。
 */
@Schema(description = "删除 Tracker 命令（软删；204 幂等）")
public record DeleteTrackerCommand() {
}
