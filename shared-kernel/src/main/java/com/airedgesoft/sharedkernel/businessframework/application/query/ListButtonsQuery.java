package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 按钮/操作列表查询（顶栏按钮与操作菜单的可见性、可用性投影）。
 *
 * @param target
 *            目标资源名（如 {@code requirements/623809242866016}、{@code peerReviews/1/items/9}，必填）
 */
@Schema(description = "按钮/操作列表查询（按 target×状态×用户投影）")
public record ListButtonsQuery(
        @Schema(description = "目标资源名（必填）", example = "requirements/623809242866016") String target) {
}
