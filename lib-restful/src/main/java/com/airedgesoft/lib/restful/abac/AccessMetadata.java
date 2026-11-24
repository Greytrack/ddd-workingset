package com.airedgesoft.lib.restful.abac;

import java.util.Map;
import java.util.Set;

/**
 * 单资源 / 单条目的 ABAC 裁决结果（v4-restful-api.md §19；裁决引擎未定型期间的<b>消费端契约</b>——资源 BC 只消费本结构， 严禁私自实现裁决逻辑）。
 *
 * <p>
 * 四元组语义（§19.2 wire 契约）：{@code allowedActions} = 条目级可执行动作（冒号动词来源，§19.1）；{@code writableFields} = 可写字段路径（camelCase
 * 点号路径，§19.3 第 3 步白名单）；{@code maskedFields} = 权限脱敏字段（读取恒生效 Omit，§19.2）； {@code valueConstraints} = 字段路径 → 允许写入候选值集（候选集
 * ⊆ 元数据枚举字典，§19.3 第 5 步）。{@code enforceWritable} 控制是否在写守卫中强制按 {@code writableFields} 白名单拦截（含空集全拒）。
 *
 * <p>
 * {@link #allowAll()} = 全允许缺省（{@code enforceWritable = false}，未接入裁决的端点行为不变）； {@link #restricted} /
 * {@link #denyAll(Set)} 强制启用可写白名单（{@code enforceWritable = true}，即使 {@code writableFields} 为空也 fail-closed 拒绝所有字段写）。
 */
public record AccessMetadata(Set<String> allowedActions, Set<String> writableFields, Set<String> maskedFields,
        Map<String, Set<String>> valueConstraints, boolean enforceWritable) {

    public AccessMetadata {
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        writableFields = writableFields == null ? Set.of() : Set.copyOf(writableFields);
        maskedFields = maskedFields == null ? Set.of() : Set.copyOf(maskedFields);
        valueConstraints = valueConstraints == null ? Map.of() : Map.copyOf(valueConstraints);
    }

    /** 向后兼容 4 参构造：{@code writableFields} 非空时启用白名单，空集不限制。 */
    public AccessMetadata(Set<String> allowedActions, Set<String> writableFields, Set<String> maskedFields,
            Map<String, Set<String>> valueConstraints) {
        this(allowedActions, writableFields, maskedFields, valueConstraints,
                writableFields != null && !writableFields.isEmpty());
    }

    /** 全允许缺省：不做可写白名单与值约束限制，脱敏为空（向后兼容未接入裁决的端点）。 */
    public static AccessMetadata allowAll() {
        return new AccessMetadata(Set.of(), Set.of(), Set.of(), Map.of(), false);
    }

    /** 受限裁决：强制按 {@code writableFields} 判定可写性（空集即全字段不可写，fail-closed）。 */
    public static AccessMetadata restricted(Set<String> allowedActions, Set<String> writableFields,
            Set<String> maskedFields, Map<String, Set<String>> valueConstraints) {
        return new AccessMetadata(allowedActions, writableFields, maskedFields, valueConstraints, true);
    }

    /** 全拒绝兜底：不可执行任何动作、不可写入任何字段，仅保留声明的读脱敏字段。 */
    public static AccessMetadata denyAll(Set<String> maskedFields) {
        return new AccessMetadata(Set.of(), Set.of(), maskedFields, Map.of(), true);
    }
}
