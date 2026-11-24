package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 产品空间分类 Tracker 树投影查询（向导树 / 左树共用端点 {@code GET /v1/productSpaces/{id}/trackerTree}）。
 *
 * @param intent
 *            投影语境：WORKINGSET_CREATE（向导第 2 步）/ WORKINGSET_EXTEND（添加产品）——附加可加入性与分支信息；缺省 = 左树
 * @param workingsetId
 *            工作集 id（树按工作集语境过滤；可选）
 * @param baselineId
 *            基线 id（按基线语境查看；可选）
 * @param searchKey
 *            关键字过滤（左树搜索；可选）
 */
@Schema(description = "分类 Tracker 树投影查询（向导树/左树共用；intent 区分语境）")
public record ProductSpaceTrackerTreeQuery(
        @Schema(description = "投影语境：WORKINGSET_CREATE / WORKINGSET_EXTEND；缺省 = 左树") String intent,
        @Schema(description = "工作集 id（可选）") String workingsetId, @Schema(description = "基线 id（可选）") String baselineId,
        @Schema(description = "关键字过滤（可选）") String searchKey) {
}
