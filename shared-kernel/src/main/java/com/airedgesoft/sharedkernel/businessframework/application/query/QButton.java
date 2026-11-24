package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 按钮/操作投影行（原详情 {@code _access.allowedActions} 内嵌下发演进为独立按钮资源投影）。
 *
 * @param key
 *            操作 key（动作标识，与冒号动词同源，如 checkout）
 * @param displayName
 *            按钮显示名
 * @param sortOrder
 *            排序号
 * @param disabledReason
 *            禁用原因（可用性 = disabledReason 为空；终态集行不可编辑等判定在此表达）
 */
@Schema(description = "按钮/操作投影行（非资源行）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QButton(@Schema(description = "操作 key（与冒号动词同源）", example = "checkout") String key,
        @Schema(description = "按钮显示名") String displayName, @Schema(description = "排序号") Integer sortOrder,
        @Schema(description = "禁用原因（为空 = 可用）") String disabledReason) {
}
