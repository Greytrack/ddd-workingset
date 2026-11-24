package com.airedgesoft.lib.restful.fieldtoggle;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.FieldViolation;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 停用预定义字段违规组装（规范 §20；读投影掩码与写掩码两通道共用）：掩码引用停用字段 → 400 {@link DomainRestCode#PREDEFINED_FIELD_DISABLED} + 逐字段
 * {@code fieldViolations}（字段不存在语义）。
 */
public final class FieldToggleViolations {

    private FieldToggleViolations() {
    }

    /**
     * 断言掩码路径未引用停用字段：未引用 → 静默返回；引用 → 抛 {@link DomainException}（携带全部命中的 {@code fieldViolations}，首个命中字段作为提示词参数）。
     */
    public static void assertPathsEnabled(Iterable<String> maskPaths, Set<String> disabledFields) {
        if (disabledFields.isEmpty()) {
            return;
        }
        Set<String> referenced = new LinkedHashSet<>();
        for (String path : maskPaths) {
            if (disabledFields.contains(path)) {
                referenced.add(path);
            }
        }
        if (referenced.isEmpty()) {
            return;
        }
        List<FieldViolation> violations = referenced.stream()
                .map(path -> FieldViolation.of(path, DomainRestCode.PREDEFINED_FIELD_DISABLED, path)).toList();
        throw new DomainException(DomainRestCode.PREDEFINED_FIELD_DISABLED, violations.get(0).field())
                .withFieldViolations(violations);
    }
}
