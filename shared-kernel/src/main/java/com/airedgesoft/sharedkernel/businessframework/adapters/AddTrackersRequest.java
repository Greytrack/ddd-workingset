package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.AddTrackersCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code POST /v1/workingsets/{id}:addTrackers} 请求体（候选树经 intent=WORKINGSET_EXTEND 投影；产品空间随分支携带，创建期挂载走新建向导）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "添加 Tracker 至工作集请求（幂等键必接；已存在分支幂等跳过）")
public record AddTrackersRequest(@Schema(description = "待添加分支清单（顺序即挂载顺序）") List<Branch> branches,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    /** 工作集分支：产品空间 × Tracker × 分支类型。 */
    @Schema(description = "工作集分支")
    public record Branch(@Schema(description = "产品空间 id") String productSpaceId,
            @Schema(description = "Tracker id") String trackerId,
            @Schema(description = "分支类型：SHARED（共享）/ INCLUDED（含入）", example = "SHARED") String branchType) {
    }

    public AddTrackersCommand toCommand() {
        return new AddTrackersCommand(branches == null
                ? null
                : branches.stream()
                        .map(b -> new AddTrackersCommand.Branch(b.productSpaceId(), b.trackerId(), b.branchType()))
                        .toList());
    }
}
