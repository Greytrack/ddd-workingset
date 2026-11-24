package com.airedgesoft.sharedkernel.workingset.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.workingset.application.command.UpdateWorkingsetCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /v1/workingsets/{id}} 请求体（编辑基本信息；掩码白名单 = displayName / owner / description / color，规范 §3）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "更新工作集请求（仅 updateMask 声明的字段参与变更；updateMask 可选：省略=全部已填充字段、*=全量替换、空串=no-op）")
public record UpdateWorkingsetRequest(@Schema(description = "工作集展示名") String displayName,
        @Schema(description = "所有者 id（十进制字符串）") String owner, @Schema(description = "描述") String description,
        @Schema(description = "颜色标记") String color,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    public UpdateWorkingsetCommand toCommand() {
        return new UpdateWorkingsetCommand(displayName, owner, description, color);
    }
}
