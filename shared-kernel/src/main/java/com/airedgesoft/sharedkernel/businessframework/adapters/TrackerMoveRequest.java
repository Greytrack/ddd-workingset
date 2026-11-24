package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.TrackerMoveCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /v1/trackers/{id}:move} 请求体（左树拖拽排序；position = 目标锚点相对位置）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "Tracker 拖拽排序请求（幂等键必接）")
public record TrackerMoveRequest(@Schema(description = "目标锚点 Tracker id") String targetTrackerId,
        @Schema(description = "相对位置：BEFORE / AFTER") String position,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    public TrackerMoveCommand toCommand() {
        return new TrackerMoveCommand(targetTrackerId, position);
    }
}
