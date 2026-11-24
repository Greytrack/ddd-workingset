package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 投票规则（双侧规则模型：同意侧满足 → 条目自动「已审核」并回写被审对象目标状态；反对侧满足 → 条目自动回退「草稿」；两侧阈值独立设定）。
 *
 * @param approveRule
 *            同意侧规则：ALL（全部审批人同意）/ MAJORITY（过半同意）/ LEADER_DECIDE（组长裁定）
 * @param rejectRule
 *            反对侧规则：ALL（全部审批人反对）/ MAJORITY（过半反对）/ LEADER_DECIDE（组长裁定）
 */
@Schema(description = "投票规则（双侧规则模型，详情只读）")
public record QVotingRules(@Schema(description = "同意侧规则：ALL / MAJORITY / LEADER_DECIDE") String approveRule,
        @Schema(description = "反对侧规则：ALL / MAJORITY / LEADER_DECIDE") String rejectRule) {
}
