package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tracker 配置查询（DEFAULT_LAYOUT、引用配置；字段定义渲染属 PageView 范畴；ETag 由端点承载）。
 *
 * @param id
 *            Tracker id（路径标识）
 */
@Schema(description = "Tracker 配置查询")
public record GetTrackerQuery(@Schema(description = "Tracker id（路径标识）") String id) {
}
