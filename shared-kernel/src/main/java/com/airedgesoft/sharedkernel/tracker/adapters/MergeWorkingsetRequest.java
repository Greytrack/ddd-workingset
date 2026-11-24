package com.airedgesoft.sharedkernel.tracker.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.tracker.application.command.MergeWorkingsetCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code POST /v1/trackers/{id}:merge} 请求体（把源工作集的分支内容并入当前工作集；双向修改冲突 409+明细；同步执行）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "工作集合并请求（幂等键必接；双向修改冲突 409 + 冲突明细）")
public record MergeWorkingsetRequest(@Schema(description = "源工作集 id（必填，十进制字符串）") String sourceWorkingsetId,
        @Schema(description = "限定合并的产品空间 id（可选）") String spaceId,
        @Schema(description = "限定的分类 id（可选）") String subclassId,
        @Schema(description = "限定合并的需求 id 集合（可选；缺省按范围全量）") List<String> itemIds,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    public MergeWorkingsetCommand toCommand() {
        return new MergeWorkingsetCommand(sourceWorkingsetId, spaceId, subclassId, itemIds);
    }
}
