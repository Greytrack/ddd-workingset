package com.airedgesoft.sharedkernel.businessframework.application.command;

/**
 * 修订评论命令（PATCH updateMask=content；仅作者本人 403）。
 *
 * @param content
 *            修订后的评论内容
 */
public record ModifyCommentCommand(String content) {
}
