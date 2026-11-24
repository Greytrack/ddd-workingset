package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 工作集比较结果（Tracker 一次性对比出参）：Tracker 节点字段差异 + 全部条目级差异，一次下发两级粒度。
 *
 * @param sourceWorkingsetId
 *            源工作集 id（路径所属工作集语境）
 * @param targetWorkingsetId
 *            对端工作集 id
 * @param trackerDiffs
 *            Tracker 级差异（分类节点字段，如展示名/树位置）
 * @param itemDiffs
 *            条目级差异（TrackerItem，变更类型 CREATED / MODIFIED / DELETED / MOVED）
 */
@Schema(description = "工作集比较结果（Tracker 级 + 条目级两级差异，一次对比）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QWorkingsetCompareResult(@Schema(description = "源工作集 id（路径所属工作集语境）") String sourceWorkingsetId,
        @Schema(description = "对端工作集 id") String targetWorkingsetId,
        @Schema(description = "Tracker 级差异（分类节点字段）") List<FieldDiff> trackerDiffs,
        @Schema(description = "条目级差异（TrackerItem）") List<ItemDiff> itemDiffs) {

    /** Tracker 级字段差异。 */
    @Schema(description = "Tracker 级字段差异")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldDiff(@Schema(description = "字段名（camelCase）") String field,
            @Schema(description = "字段展示名") String displayName, @Schema(description = "本侧值") String sourceValue,
            @Schema(description = "对端值") String targetValue) {
    }

    /** 条目级差异：一条需求的变更事实。 */
    @Schema(description = "条目级差异")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemDiff(
            @Schema(description = "变更类型：CREATED / MODIFIED / DELETED / MOVED", example = "MODIFIED") String changeType,
            @Schema(description = "需求编号（业务键，如 R00099）") String number,
            @Schema(description = "需求展示名") String displayName,
            @Schema(description = "移动场景的目标父需求 id（changeType=MOVED 时下发）") String targetParentId) {
    }
}
