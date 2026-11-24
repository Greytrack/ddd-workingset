package com.airedgesoft.sharedkernel.businessframework.infrastructure.association;

import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;
import com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationRule;
import com.airedgesoft.sharedkernel.businessframework.domain.association.EndSide;
import com.airedgesoft.sharedkernel.businessframework.infrastructure.association.AssociationContextProvider.AssociationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 关联规则底座映射（infrastructure 私有）：{@code air_inst_relation_rule} + {@code air_inst_relation_rule_field_value} +
 * {@code air_inst_query_criteria} 三表读写（v3 {@code InstRelationRuleService} 落库口径）。
 *
 * <p>
 * <b>已知口径缺口</b>：v3 三表的 {@code attr_id} 由对象生命周期相位属性元数据推导；v4 尚无该元数据，本轮写 NULL（值列照 v3 写入）。
 */
final class AssociationRuleMapper {

    private AssociationRuleMapper() {
    }

    /** 按关联行 id 批量读取规则（无规则的行缺席）。 */
    static Map<Long, AssociationRule> loadRules(JdbcTemplate jdbcTemplate, Collection<Long> associationIds) {
        if (associationIds == null || associationIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(associationIds.size());
        List<Map<String, Object>> ruleRows = jdbcTemplate.queryForList("SELECT " + AssociationStorage.RULE_ID + ", "
                + AssociationStorage.RULE_RELATIONSHIP_ID + ", " + AssociationStorage.RULE_DRIVE_TYPE + ", "
                + AssociationStorage.RULE_EDIT_RULE_NAME + " FROM " + AssociationStorage.RULE_TABLE + " WHERE "
                + AssociationStorage.RULE_RELATIONSHIP_ID + " IN (" + placeholders + ")", associationIds.toArray());
        if (ruleRows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> relationshipToRule = new HashMap<>();
        Map<Long, Map<String, Object>> ruleById = new HashMap<>();
        for (Map<String, Object> ruleRow : ruleRows) {
            Long ruleId = asLong(ruleRow.get(AssociationStorage.RULE_ID));
            Long relationshipId = asLong(ruleRow.get(AssociationStorage.RULE_RELATIONSHIP_ID));
            relationshipToRule.put(relationshipId, ruleId);
            ruleById.put(ruleId, ruleRow);
        }
        Map<Long, String> triggerValues = valueByOwner(jdbcTemplate, AssociationStorage.QUERY_CRITERIA_TABLE,
                AssociationStorage.CRITERIA_QUERY_ID, ruleById.keySet());
        Map<Long, String> resultValues = valueByOwner(jdbcTemplate, AssociationStorage.RULE_FIELD_VALUE_TABLE,
                AssociationStorage.FIELD_VALUE_RELATION_RULE_ID, ruleById.keySet());
        Map<Long, AssociationRule> rules = new LinkedHashMap<>();
        relationshipToRule.forEach((relationshipId, ruleId) -> {
            Map<String, Object> ruleRow = ruleById.get(ruleId);
            Long driveType = asLong(ruleRow.get(AssociationStorage.RULE_DRIVE_TYPE));
            boolean driverFirst = driveType != null && driveType == 1L;
            rules.put(relationshipId,
                    new AssociationRule(stringValue(ruleRow.get(AssociationStorage.RULE_EDIT_RULE_NAME)), 0, driveType,
                            driverFirst ? EndSide.SOURCE : EndSide.TARGET, triggerValues.get(ruleId),
                            driverFirst ? EndSide.TARGET : EndSide.SOURCE, resultValues.get(ruleId)));
        });
        return rules;
    }

    /** 同步规则底座（与关联行同事务）：有规则→upsert + 重建条件/值行；无规则→删除三表。 */
    static void syncRules(JdbcTemplate jdbcTemplate, List<Association> rows, Map<Long, AssociationContext> contexts,
            long now) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, Long> existing = existingRuleIds(jdbcTemplate,
                rows.stream().map(Association::associationId).toList());
        for (Association row : rows) {
            Long ruleId = existing.get(row.associationId());
            if (row.getRule().isEmpty()) {
                if (ruleId != null) {
                    deleteRule(jdbcTemplate, ruleId);
                }
                continue;
            }
            AssociationRule rule = row.getRule().orElseThrow();
            AssociationContext context = contexts.get(row.associationId());
            if (ruleId == null) {
                ruleId = Ids.nextId();
                jdbcTemplate.update("INSERT INTO " + AssociationStorage.RULE_TABLE + " (" + AssociationStorage.RULE_ID
                        + ", " + AssociationStorage.RULE_INSTANCE_ID + ", " + AssociationStorage.RULE_INSTANCE_TYPE
                        + ", " + AssociationStorage.RULE_INSTANCE_NUMBER + ", "
                        + AssociationStorage.RULE_RELATIONSHIP_ID + ", " + AssociationStorage.RULE_RELATED_INSTANCE_ID
                        + ", " + AssociationStorage.RULE_RELATED_TYPE + ", " + AssociationStorage.RULE_RELATED_NUMBER
                        + ", " + AssociationStorage.RULE_DRIVE_TYPE + ", " + AssociationStorage.RULE_EXECUTED + ", "
                        + AssociationStorage.LOCK_VERSION + ", " + AssociationStorage.CREATED_TIME + ", "
                        + AssociationStorage.UPDATED_TIME + ", " + AssociationStorage.RULE_EDIT_RULE_NAME + ", "
                        + AssociationStorage.TRACKER + ")" + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", ruleId,
                        row.sourceId(), row.sourceType(), context == null ? null : context.sourceNumber(),
                        row.associationId(), row.targetId(), row.targetType(),
                        context == null ? null : context.targetNumber(), rule.driveType(), 0, 0L, now, now,
                        rule.editRuleName(), context == null ? null : context.sourceTracker());
            } else {
                jdbcTemplate.update(
                        "UPDATE " + AssociationStorage.RULE_TABLE + " SET " + AssociationStorage.RULE_DRIVE_TYPE
                                + " = ?, " + AssociationStorage.RULE_EDIT_RULE_NAME + " = ?, "
                                + AssociationStorage.RULE_EXECUTED + " = ?, " + AssociationStorage.UPDATED_TIME
                                + " = ? WHERE " + AssociationStorage.RULE_ID + " = ?",
                        rule.driveType(), rule.editRuleName(), 0, now, ruleId);
                jdbcTemplate.update("DELETE FROM " + AssociationStorage.QUERY_CRITERIA_TABLE + " WHERE "
                        + AssociationStorage.CRITERIA_QUERY_ID + " = ?", ruleId);
                jdbcTemplate.update("DELETE FROM " + AssociationStorage.RULE_FIELD_VALUE_TABLE + " WHERE "
                        + AssociationStorage.FIELD_VALUE_RELATION_RULE_ID + " = ?", ruleId);
            }
            insertRuleValueRows(jdbcTemplate, ruleId, row, rule, context, now);
        }
    }

    /** 按关联行 id 删除规则底座（三表）。 */
    static void removeRules(JdbcTemplate jdbcTemplate, Collection<Long> associationIds) {
        if (associationIds == null || associationIds.isEmpty()) {
            return;
        }
        Map<Long, Long> existing = existingRuleIds(jdbcTemplate, associationIds);
        for (Long ruleId : existing.values()) {
            deleteRule(jdbcTemplate, ruleId);
        }
    }

    private static void insertRuleValueRows(JdbcTemplate jdbcTemplate, Long ruleId, Association row,
            AssociationRule rule, AssociationContext context, long now) {
        Long tracker = context == null ? null : context.sourceTracker();
        // v3 口径：条件/结果列的属性 id 取对应生命周期相位属性（触发端/结果端各自对象的元数据）。
        Long triggerAttributeId = phaseAttributeId(context, rule.triggerEnd());
        Long resultAttributeId = phaseAttributeId(context, rule.resultEnd());
        if (rule.triggerPhase() != null) {
            jdbcTemplate.update(
                    "INSERT INTO " + AssociationStorage.QUERY_CRITERIA_TABLE + " (" + AssociationStorage.RULE_ID + ", "
                            + AssociationStorage.CRITERIA_QUERY_ID + ", " + AssociationStorage.CRITERIA_ATTR_ID + ", "
                            + AssociationStorage.CRITERIA_VALUE + ", " + AssociationStorage.LOCK_VERSION + ", "
                            + AssociationStorage.CREATED_TIME + ", " + AssociationStorage.UPDATED_TIME + ", "
                            + AssociationStorage.TRACKER + ")" + " VALUES (?,?,?,?,?,?,?,?)",
                    Ids.nextId(), ruleId, triggerAttributeId, rule.triggerPhase(), 0L, now, now, tracker);
        }
        if (rule.resultPhase() != null) {
            jdbcTemplate.update(
                    "INSERT INTO " + AssociationStorage.RULE_FIELD_VALUE_TABLE + " (" + AssociationStorage.RULE_ID
                            + ", " + AssociationStorage.FIELD_VALUE_RELATION_RULE_ID + ", "
                            + AssociationStorage.FIELD_VALUE_ATTR_ID + ", " + AssociationStorage.FIELD_VALUE_VALUE
                            + ", " + "source_value" + ", " + AssociationStorage.LOCK_VERSION + ", "
                            + AssociationStorage.CREATED_TIME + ", " + AssociationStorage.UPDATED_TIME + ", "
                            + AssociationStorage.TRACKER + ")" + " VALUES (?,?,?,?,?,?,?,?,?)",
                    Ids.nextId(), ruleId, resultAttributeId, rule.resultPhase(), rule.resultPhase(), 0L, now, now,
                    tracker);
        }
    }

    /** 端 → 生命周期相位属性 id（上下文缺失时返回 null）。 */
    private static Long phaseAttributeId(AssociationContext context, EndSide side) {
        if (context == null || side == null) {
            return null;
        }
        return side == EndSide.SOURCE ? context.sourcePhaseAttributeId() : context.targetPhaseAttributeId();
    }

    private static Map<Long, Long> existingRuleIds(JdbcTemplate jdbcTemplate, Collection<Long> associationIds) {
        String placeholders = placeholders(associationIds.size());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT " + AssociationStorage.RULE_ID + ", "
                + AssociationStorage.RULE_RELATIONSHIP_ID + " FROM " + AssociationStorage.RULE_TABLE + " WHERE "
                + AssociationStorage.RULE_RELATIONSHIP_ID + " IN (" + placeholders + ")", associationIds.toArray());
        Map<Long, Long> existing = new HashMap<>();
        for (Map<String, Object> row : rows) {
            existing.put(asLong(row.get(AssociationStorage.RULE_RELATIONSHIP_ID)),
                    asLong(row.get(AssociationStorage.RULE_ID)));
        }
        return existing;
    }

    private static void deleteRule(JdbcTemplate jdbcTemplate, Long ruleId) {
        jdbcTemplate.update("DELETE FROM " + AssociationStorage.QUERY_CRITERIA_TABLE + " WHERE "
                + AssociationStorage.CRITERIA_QUERY_ID + " = ?", ruleId);
        jdbcTemplate.update("DELETE FROM " + AssociationStorage.RULE_FIELD_VALUE_TABLE + " WHERE "
                + AssociationStorage.FIELD_VALUE_RELATION_RULE_ID + " = ?", ruleId);
        jdbcTemplate.update(
                "DELETE FROM " + AssociationStorage.RULE_TABLE + " WHERE " + AssociationStorage.RULE_ID + " = ?",
                ruleId);
    }

    private static Map<Long, String> valueByOwner(JdbcTemplate jdbcTemplate, String table, String ownerColumn,
            Collection<Long> owners) {
        String placeholders = placeholders(owners.size());
        List<Map<String, Object>> rows = jdbcTemplate
                .queryForList("SELECT " + ownerColumn + ", " + AssociationStorage.CRITERIA_VALUE + " FROM " + table
                        + " WHERE " + ownerColumn + " IN (" + placeholders + ")", owners.toArray());
        Map<Long, String> values = new HashMap<>();
        for (Map<String, Object> row : rows) {
            values.putIfAbsent(asLong(row.get(ownerColumn)), stringValue(row.get(AssociationStorage.CRITERIA_VALUE)));
        }
        return values;
    }

    private static String placeholders(int size) {
        List<String> marks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            marks.add("?");
        }
        return String.join(",", marks);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
