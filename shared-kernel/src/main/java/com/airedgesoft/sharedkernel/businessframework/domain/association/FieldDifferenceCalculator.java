package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessDataCarrier;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;

import java.util.*;

/**
 * 领域策略（纯计算 · 无 IO）：字段差异计算（关联稿类图 §二 {@code FieldDifferenceCalculator}）。
 *
 * <p>
 * 以「源侧（before）→ 目标侧（after）」为语义：输出目标侧相对源侧不同的字段；单侧缺失也计为差异（before/after 对应 null）。 比较口径为 {@link FieldValue#value()} 的串形态（v3
 * {@code DataObjectComparison} 展示口径）。
 */
public class FieldDifferenceCalculator {

    /**
     * 计算差异集。
     *
     * @param source
     *            源侧业务数据（before）
     * @param target
     *            目标侧业务数据（after）
     * @return 差异项（按源侧列顺序，目标侧新增列排后）
     */
    public List<FieldChange> calculate(BusinessDataCarrier source, BusinessDataCarrier target) {
        Objects.requireNonNull(target, "target");
        Set<String> columns = columns(source, target);

        List<FieldChange> changes = new ArrayList<>();
        for (String column : columns) {
            FieldValue before = source == null ? null : source.getBusinessData().get(column);
            FieldValue after = target.getBusinessData().get(column);
            if (sameValue(before, after)) {
                continue;
            }
            changes.add(new FieldChange(column, before, after));
        }
        return changes;
    }

    /**
     * 参与比对的属性总数（两端列并集；差异集统计用）。
     *
     * @param source
     *            源侧业务数据
     * @param target
     *            目标侧业务数据
     * @return 列并集大小
     */
    public int totalAttributes(BusinessDataCarrier source, BusinessDataCarrier target) {
        Objects.requireNonNull(target, "target");
        return columns(source, target).size();
    }

    private Set<String> columns(BusinessDataCarrier source, BusinessDataCarrier target) {
        Set<String> columns = new LinkedHashSet<>();
        if (source != null) {
            columns.addAll(source.getAllFields().keySet());
        }
        columns.addAll(target.getAllFields().keySet());
        return columns;
    }

    private boolean sameValue(FieldValue before, FieldValue after) {
        if (before == null || after == null) {
            return before == after;
        }
        return Objects.equals(before.value().toText(), after.value().toText());
    }
}
