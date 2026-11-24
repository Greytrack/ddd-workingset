package com.airedgesoft.sharedkernel.businessframework.infrastructure.association;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.jdbc.StoredRow;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;
import com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationRule;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspect;
import com.airedgesoft.sharedkernel.businessframework.infrastructure.association.AssociationContextProvider.AssociationContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 关联行存储映射（infrastructure 私有）：{@code air_inst_tab_association} 列 ↔ 聚合双向换算。
 *
 * <p>
 * <b>v3 产物口径</b>（2026-11-23 只读考古）：单版次插入只写系统列 + 业务属性列；{@code revision_in/revision_out/flags/bit_flags/
 * edit_row/current_head/related_revision_id/attributes} 不写（保持 NULL）。读侧对 NULL 宽容。
 */
final class AssociationMapper {

    private AssociationMapper() {
    }

    /** 从 v3 行重建关联聚合。 */
    static Association reconstitute(StoredRow row, AssociationRule rule) {
        Long associationId = asLong(row.values().get(AssociationStorage.ID));
        Long sourceId = asLong(row.values().get(AssociationStorage.INSTANCE_ID));
        Long sourceType = asLong(row.values().get(AssociationStorage.INSTANCE_TYPE));
        Long targetId = asLong(row.values().get(AssociationStorage.RELATED_INSTANCE_ID));
        Long targetType = asLong(row.values().get(AssociationStorage.RELATED_TYPE));
        Long associationType = asLong(row.values().get(AssociationStorage.RELATIONSHIP_TYPE));
        Long propagationType = asLong(row.values().get(AssociationStorage.PROPAGATION_TYPE));
        Long upstreamMerge = asLong(row.values().get(AssociationStorage.UPSTREAM_MERGE_REVISION));
        Long downstreamMerge = asLong(row.values().get(AssociationStorage.DOWNSTREAM_MERGE_REVISION));
        boolean upstreamChanged = truthy(row.values().get(AssociationStorage.UPSTREAM_CHANGED));
        boolean downstreamChanged = truthy(row.values().get(AssociationStorage.DOWNSTREAM_CHANGED));
        PropagationSuspect suspect = new PropagationSuspect(sourceId, targetId,
                propagationType == null ? Association.DEFAULT_PROPAGATION_TYPE : propagationType, upstreamChanged,
                downstreamChanged, upstreamMerge, downstreamMerge);
        // 关联类型列缺失（历史脏数据）按缺省「依存于」兜底，保持聚合不变量。
        Long effectiveAssociationType = associationType == null
                ? Association.DEFAULT_ASSOCIATION_TYPE
                : associationType;
        return Association.reconstitute(associationId, sourceId, sourceType, targetId, targetType,
                effectiveAssociationType, suspect, rule, optionalValuesFrom(row));
    }

    /** 新增行系统列 + 业务列（v3 单版次插入口径；上下文只在插入时填充）。 */
    static Map<String, Object> insertValues(Association row, AssociationContext context, long now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(AssociationStorage.ID, row.associationId());
        values.put(AssociationStorage.INSTANCE_ID, row.sourceId());
        values.put(AssociationStorage.INSTANCE_TYPE, row.sourceType());
        values.put(AssociationStorage.COMMON_ID, context.sourceCommonId());
        values.put(AssociationStorage.WORKINGSET_ID, context.sourceWorkingsetId());
        values.put(AssociationStorage.WORK_SPACE, context.sourceWorkSpace());
        values.put(AssociationStorage.TRACKER, context.sourceTracker());
        values.put(AssociationStorage.RELATED_INSTANCE_ID, row.targetId());
        values.put(AssociationStorage.RELATED_TYPE, row.targetType());
        values.put(AssociationStorage.RELATED_COMMON_ID, context.targetCommonId());
        values.put(AssociationStorage.RELATED_WORKINGSET_ID, context.targetWorkingsetId());
        values.put(AssociationStorage.RELATED_WORK_SPACE, context.targetWorkSpace());
        values.put(AssociationStorage.RELATED_TRACKER, context.targetTracker());
        values.put(AssociationStorage.DIRECTION, AssociationStorage.DIRECTION_HOST_SIDE);
        values.put(AssociationStorage.TAB_ID, context.sourceTabId());
        values.put(AssociationStorage.LOCK_VERSION, 0L);
        values.put(AssociationStorage.CREATED_TIME, now);
        values.put(AssociationStorage.UPDATED_TIME, now);
        putBusinessValues(values, row, now);
        return values;
    }

    /** 修改行：只动业务列 + 审计列（上下文/创建列不动）。 */
    static Map<String, Object> updateValues(Association row, long now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(AssociationStorage.ID, row.associationId());
        values.put(AssociationStorage.UPDATED_TIME, now);
        putBusinessValues(values, row, now);
        return values;
    }

    /** 可选属性值（键＝落库列名；仅取命中的列）。 */
    static Map<String, FieldValue> optionalValuesFrom(StoredRow row) {
        Map<String, FieldValue> values = new LinkedHashMap<>();
        for (String column : AssociationStorage.OPTIONAL_COLUMNS) {
            Object stored = row.values().get(column);
            if (stored != null) {
                values.put(column, FieldValue.of(column, stored));
            }
        }
        return values;
    }

    private static void putBusinessValues(Map<String, Object> values, Association row, long now) {
        PropagationSuspect suspect = row.propagationSuspect();
        values.put(AssociationStorage.RELATIONSHIP_TYPE, row.associationType());
        values.put(AssociationStorage.PROPAGATION_TYPE, suspect.propagationType());
        values.put(AssociationStorage.UPSTREAM_CHANGED, suspect.upstreamChanged() ? 1L : 0L);
        values.put(AssociationStorage.DOWNSTREAM_CHANGED, suspect.downstreamChanged() ? 1L : 0L);
        values.put(AssociationStorage.UPSTREAM_MERGE_REVISION, suspect.upstreamMergeRevision());
        values.put(AssociationStorage.DOWNSTREAM_MERGE_REVISION, suspect.downstreamMergeRevision());
        row.getRule().ifPresentOrElse(rule -> {
            values.put(AssociationStorage.EDIT_RULE, 0);
            values.put(AssociationStorage.EDIT_RULE_NAME, rule.editRuleName());
        }, () -> {
            values.put(AssociationStorage.EDIT_RULE, null);
            values.put(AssociationStorage.EDIT_RULE_NAME, null);
        });
        for (String column : AssociationStorage.OPTIONAL_COLUMNS) {
            values.put(column, columnValue(column, row.optionalValues().get(column)));
        }
    }

    /** 可选属性值 → 列值（按列族转换；空值落 NULL）。 */
    static Object columnValue(String column, FieldValue fieldValue) {
        if (fieldValue == null || fieldValue.value() == null || fieldValue.value().isEmpty()) {
            return null;
        }
        String text = fieldValue.value().toText();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (column.startsWith("numeric") || column.startsWith("compositenumber")) {
            if (column.startsWith("numeric")) {
                try {
                    return new BigDecimal(text.trim());
                } catch (NumberFormatException e) {
                    throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                            SharedKernelResultCode.ATTRIBUTE_VALUE_FORMAT_INVALID, column, text);
                }
            }
            return text;
        }
        if (column.startsWith("date") || column.startsWith("startdate") || column.startsWith("endstartdate")
                || column.startsWith("list")) {
            if (column.startsWith("multilist")) {
                return text;
            }
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                        SharedKernelResultCode.ATTRIBUTE_VALUE_FORMAT_INVALID, column, text);
            }
        }
        return text;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return number.longValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
