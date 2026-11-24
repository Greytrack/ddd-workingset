package com.airedgesoft.sharedkernel.workingset.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.workingset.application.command.ReplaceWorkingsetContentCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /v1/workingsets/{id}:replaceContent} 请求体（GateFlow 回滚场景，legacy createRollbackWorkingset 异步 Job 实证；LRO 承载）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验（受理时校验）：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "工作集内容替换请求（LRO：202 + operations；幂等键必接）")
public record ReplaceWorkingsetContentRequest(@Schema(description = "源工作集 id（其内容替换本工作集）") String sourceWorkingsetId,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    public ReplaceWorkingsetContentCommand toCommand() {
        return new ReplaceWorkingsetContentCommand(sourceWorkingsetId);
    }
}
