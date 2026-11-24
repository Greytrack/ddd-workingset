package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作集列表查询（顶栏工作集选择器树数据源：平铺集合，节点带 parentId + canView）。
 *
 * @param parentId
 *            父工作集 id（可选；缺省返回全量平铺集合，前端按 parentId 组树）
 * @param pageSize
 *            单页数量（缺省取系统配置；0 取默认；负数或非数字 → 400）
 * @param pageToken
 *            下一页游标（首页不传）
 */
@Schema(description = "工作集列表查询（平铺集合，§13）")
public record ListWorkingsetsQuery(@Schema(description = "父工作集 id（可选；缺省全量）") String parentId,
        @Schema(description = "单页数量（缺省取系统配置）") Integer pageSize,
        @Schema(description = "下一页游标（首页不传）") String pageToken) {
}
