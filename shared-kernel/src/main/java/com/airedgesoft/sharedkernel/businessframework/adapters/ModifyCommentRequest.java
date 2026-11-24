package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.LockedCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.ModifyCommentCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /v1/comments/{id}} 请求体（updateMask=content；仅作者本人 403）。
 *
 * <p>
 * {@link LockedCommand} 由框架强制校验：{@code lockedVersion} 必填，缺失 / 非正整数 → 400，与资源当前代数不一致 → 409。
 */
@Schema(description = "修订评论请求（仅 updateMask=content）")
public record ModifyCommentRequest(@Schema(description = "修订后的评论内容") String content,
        @Schema(description = "乐观并发版本（必填，>= 1，取自资源当前 lockedVersion）", example = "1") Long lockedVersion)
        implements
            LockedCommand {

    public ModifyCommentCommand toCommand() {
        return new ModifyCommentCommand(content);
    }
}
