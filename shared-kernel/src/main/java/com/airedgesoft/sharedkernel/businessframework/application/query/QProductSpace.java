package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 产品空间读模型（v4 契约）：本轮只读（写侧为反例行不做）。
 *
 * @param name
 *            AIP-122 资源名（相对形态，如 productSpaces/623809242866016）
 * @param id
 *            产品空间 id（十进制字符串）
 * @param displayName
 *            产品空间展示名（AIP-148）
 * @param description
 *            描述
 * @param createTime
 *            创建时刻（RFC 3339，output-only）
 * @param updateTime
 *            最近更新时刻（RFC 3339，output-only）
 */
@Schema(description = "产品空间资源（本轮只读）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QProductSpace(@Schema(description = "AIP-122 资源名（相对形态，如 productSpaces/623809242866016）") String name,
        @Schema(description = "产品空间 id（十进制字符串）") String id, @Schema(description = "产品空间展示名") String displayName,
        @Schema(description = "描述") String description,
        @Schema(description = "创建时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String createTime,
        @Schema(description = "最近更新时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String updateTime) {
}
