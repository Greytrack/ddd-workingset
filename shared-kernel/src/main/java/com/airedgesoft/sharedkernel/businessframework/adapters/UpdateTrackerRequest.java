package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.UpdateTrackerCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /v1/trackers/{id}} 请求体（配置编辑；掩码白名单 = displayName / instanceTreeShowContent，规范 §3）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "更新 Tracker 请求（仅 updateMask 声明的字段参与变更；updateMask 可选：省略=全部已填充字段、*=全量替换、空串=no-op；幂等键必接）")
public record UpdateTrackerRequest(@Schema(description = "Tracker 展示名") String displayName,
        @Schema(description = "树节点显示内容配置") String instanceTreeShowContent,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    public UpdateTrackerCommand toCommand() {
        return new UpdateTrackerCommand(displayName, instanceTreeShowContent);
    }
}
