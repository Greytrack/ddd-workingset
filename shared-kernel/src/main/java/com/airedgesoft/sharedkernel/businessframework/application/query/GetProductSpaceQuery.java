package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 产品空间详情查询（概览/职能团队/子项/分类/操作记录页签；ETag 由端点承载）。
 *
 * @param id
 *            产品空间 id（路径标识）
 */
@Schema(description = "产品空间详情查询")
public record GetProductSpaceQuery(@Schema(description = "产品空间 id（路径标识）") String id) {
}
