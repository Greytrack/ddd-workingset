package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

/**
 * 值对象：关联规则（AssociationRule，0..1；关联稿 §二）。
 *
 * <p>
 * 关联行上的规则是「整体设置 / 整体删除」的 0..1 值对象（规则维护并入批量修改关联行，不单独成用例）。 值对象只做形态承载，触发语义（workflow 状态变迁）不在本轮范围内。
 *
 * <p>
 * 字段对齐 v3 规则底座：{@code driveType} → {@code air_inst_relation_rule.drive_type}；{@code triggerEnd/triggerPhase} →
 * {@code air_inst_query_criteria}（{@code attr_id/value}）；{@code resultEnd/resultPhase} →
 * {@code air_inst_relation_rule_field_value}（{@code attr_id/value}）；{@code editRuleName} → 规则表与关联行同名文本列。
 *
 * @param editRuleName
 *            规则渲染名（v3 {@code edit_rule_name}；非空）
 * @param editRule
 *            规则标记（v3 {@code edit_rule}；0＝已配置）
 * @param driveType
 *            驱动类型（v3 {@code drive_type}：驱动 / 被驱动）
 * @param triggerEnd
 *            触发端（源 / 目标）
 * @param triggerPhase
 *            触发端相位（v3 条件值）
 * @param resultEnd
 *            结果端（源 / 目标）
 * @param resultPhase
 *            结果端相位（v3 结果值）
 */
public record AssociationRule(String editRuleName, Integer editRule, Long driveType, EndSide triggerEnd,
        String triggerPhase, EndSide resultEnd, String resultPhase) {

    public AssociationRule {
        if (editRuleName == null || editRuleName.isBlank()) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.ASSOCIATION_RULE_NAME_REQUIRED);
        }
    }

}
