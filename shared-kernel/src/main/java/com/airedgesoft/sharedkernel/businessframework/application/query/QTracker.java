package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tracker 读模型（配置读；创建出参共用）。
 *
 * @param name
 *            AIP-122 资源名（相对形态，如 trackers/623809242866016）
 * @param id
 *            Tracker id（十进制字符串）
 * @param displayName
 *            Tracker 展示名
 * @param trackerClassId
 *            Tracker 分类 id
 * @param workSpaceId
 *            所属产品空间 id
 * @param parentId
 *            父节点 id（分类树层级；根为 null）
 * @param defaultViewType
 *            默认视图类型（表格 / 文档 / 卡片）
 * @param createTime
 *            创建时刻（RFC 3339，output-only）
 * @param updateTime
 *            最近更新时刻（RFC 3339，output-only）
 */
@Schema(description = "Tracker 资源（分类树节点配置）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QTracker(@Schema(description = "AIP-122 资源名（相对形态，如 trackers/623809242866016）") String name,
        @Schema(description = "Tracker id（十进制字符串）") String id, @Schema(description = "Tracker 展示名") String displayName,
        @Schema(description = "Tracker 分类 id") String trackerClassId,
        @Schema(description = "所属产品空间 id") String workSpaceId, @Schema(description = "父节点 id（根为 null）") String parentId,
        @Schema(description = "默认视图类型（表格/文档/卡片）") String defaultViewType,
        @Schema(description = "创建时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String createTime,
        @Schema(description = "最近更新时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String updateTime) {
}
