package com.airedgesoft.sharedkernel.tracker.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 还原 Tracker 命令（回收站还原，legacy reStoreTrackerFromRecycleBin 实证；200+新 ETag）。
 */
@Schema(description = "还原 Tracker 命令（仅已软删生效，否则 404）")
public record TrackerUndeleteCommand() {
}
