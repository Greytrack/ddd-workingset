package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 产品空间列表查询（只读投影；本轮产品空间无查询参数，列表全量平铺返回）。
 */
@Schema(description = "产品空间列表查询（只读）")
public record ListProductSpacesQuery() {
}
