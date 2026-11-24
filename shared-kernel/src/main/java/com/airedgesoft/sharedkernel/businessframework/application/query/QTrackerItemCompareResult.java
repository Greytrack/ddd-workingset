package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 单条目对比结果（TrackerItem 级对比出参）：一个条目与对端工作集对应条目的字段级差异。
 *
 * @param itemId
 *            条目 id（路径 id）
 * @param number
 *            需求编号（业务键）
 * @param displayName
 *            需求展示名
 * @param targetWorkingsetId
 *            对端工作集 id
 * @param fieldDiffs
 *            字段级差异
 */
@Schema(description = "单条目对比结果（字段级差异）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QTrackerItemCompareResult(@Schema(description = "条目 id（路径 id）") String itemId,
        @Schema(description = "需求编号（业务键）") String number, @Schema(description = "需求展示名") String displayName,
        @Schema(description = "对端工作集 id") String targetWorkingsetId,
        @Schema(description = "字段级差异") List<QWorkingsetCompareResult.FieldDiff> fieldDiffs) {
}
