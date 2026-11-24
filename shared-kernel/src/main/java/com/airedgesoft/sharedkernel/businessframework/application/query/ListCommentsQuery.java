package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 评论列表查询（按对象查询，评论为顶级资源；游标分页与集合查询统一信封，规范 §6/§7.1）。
 *
 * @param pageSize
 *            单页数量（缺省取系统配置）
 * @param pageToken
 *            下一页游标（首页不传）
 * @param target
 *            目标对象资源名（filter 白名单成员，等值匹配）
 * @param orderBy
 *            排序（createTime，id 兜底）
 */
@Schema(description = "评论列表查询（游标分页 + 按 target 过滤）")
public record ListCommentsQuery(@Schema(description = "单页数量") Integer pageSize,
        @Schema(description = "下一页游标（首页不传）") String pageToken,
        @Schema(description = "目标对象资源名", example = "requirements/623809242866016") String target,
        @Schema(description = "排序（createTime，id 兜底）", example = "createTime asc") String orderBy) {
}
