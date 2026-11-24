package com.airedgesoft.sharedkernel.businessframework.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 删除评论命令（仅作者或管理员可删 403；204 幂等）。
 */
@Schema(description = "删除评论命令（204 幂等）")
public record DeleteCommentCommand() {
}
