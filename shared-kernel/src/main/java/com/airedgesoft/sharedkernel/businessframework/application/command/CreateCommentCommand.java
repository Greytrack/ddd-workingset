package com.airedgesoft.sharedkernel.businessframework.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 发表评论命令（含回复；评论为顶级资源，{@code target} 为 AIP-122 资源名——评论不限于需求，评审条目复用同一资源）。
 *
 * @param target
 *            目标对象资源名（如 {@code requirements/623809242866016}、{@code peerReviews/1/items/9}；不存在 404 / 已删 409）
 * @param content
 *            评论内容（必填非空 400）
 * @param replyTo
 *            被回复评论资源名（{@code comments/{id}}；可空 = 顶级评论）
 */
@Schema(description = "发表评论命令（幂等键必接）")
public record CreateCommentCommand(
        @Schema(description = "目标对象资源名", example = "requirements/623809242866016") String target,
        @Schema(description = "评论内容（必填非空）") String content,
        @Schema(description = "被回复评论资源名（comments/{id}；可空 = 顶级评论）") String replyTo) {
}
