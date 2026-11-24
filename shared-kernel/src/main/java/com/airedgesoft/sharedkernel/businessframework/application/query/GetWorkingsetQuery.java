package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作集详情查询（编辑表单即详情回显；ETag 由端点承载）。
 *
 * @param id
 *            工作集 id（路径标识）
 */
@Schema(description = "工作集详情查询")
public record GetWorkingsetQuery(@Schema(description = "工作集 id（路径标识）") String id) {
}
