package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tracker 清单查询（分页 + filter；空集合 200）。
 *
 * @param pageSize
 *            单页数量（缺省取系统配置）
 * @param pageToken
 *            下一页游标（首页不传）
 * @param filter
 *            AIP-160 过滤（白名单 displayName/workSpaceId）
 */
@Schema(description = "Tracker 清单查询（分页 + filter）")
public record ListTrackersQuery(@Schema(description = "单页数量（缺省取系统配置）") Integer pageSize,
        @Schema(description = "下一页游标（首页不传）") String pageToken,
        @Schema(description = "AIP-160 过滤（白名单 displayName/workSpaceId）") String filter) {
}
