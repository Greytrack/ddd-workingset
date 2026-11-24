package com.airedgesoft.lib.jdbc;

import com.airedgesoft.lib.jdbc.JdbcWritePlan.Value;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 先汇总单行终值再附加版本与审计，避免一次保存中的多个动作让同一行重复递增版本。
 *
 * <p>
 * 作为编译器内部机制，它允许更新终值；不同存储贡献之间的冲突由 {@link JdbcWritePlanner.Changes} 提前检查。因此不对仓储公开这个可变收集器，防止调用方绕过受限贡献接口。
 */
final class JdbcRowChanges {
    private final Map<String, Value> values;

    /**
     * 复制字段终值，隔离调用方对差异集合的修改。
     *
     * @param values
     *            列名与绑定值一致的初始字段终值
     */
    private JdbcRowChanges(Map<String, Value> values) {
        this.values = new LinkedHashMap<>(JdbcWritePlan.immutable(values));
    }

    /**
     * 从前后投影计算字段净变化，供后续协议贡献补充终值。
     *
     * @param before
     *            修改前的列值投影
     * @param after
     *            修改后的列值投影，列范围必须与 before 一致
     * @return 仅包含变化列终值的收集器
     */
    static JdbcRowChanges between(Map<String, Value> before, Map<String, Value> after) {
        return new JdbcRowChanges(difference(before, after));
    }

    /**
     * 比较范围必须一致，避免把未加载字段当作清空；复制覆盖和普通更新共用这一口径。
     *
     * @param before
     *            修改前的比较投影
     * @param after
     *            修改后的比较投影；列名集合必须与 before 一致
     * @return 变化列到修改后绑定值的映射，无变化时为空
     */
    static Map<String, Value> difference(Map<String, Value> before, Map<String, Value> after) {
        if (!before.keySet().equals(after.keySet())) {
            throw new IllegalStateException("字段加载范围不一致");
        }
        Map<String, Value> changed = new LinkedHashMap<>();
        after.forEach((name, value) -> {
            if (!value.equals(before.get(name))) {
                changed.put(name, value);
            }
        });
        return changed;
    }

    /**
     * 将显式赋值作为初始终值，不再计算前后差异。
     *
     * @param assignments
     *            需要写入的列及其最终绑定值
     * @return 可继续补充终值、版本和审计的收集器
     */
    static JdbcRowChanges of(Map<String, Value> assignments) {
        return new JdbcRowChanges(assignments);
    }

    /**
     * 暴露当前已收集的变化范围，供贡献回调判断。
     *
     * @return 当前变化列名的不可修改副本
     */
    Set<String> columnNames() {
        return Set.copyOf(values.keySet());
    }

    /**
     * 调用方显式确定列终值；允许替换先前终值，不等价于多个策略无冲突地合并。
     *
     * @param value
     *            需要写入的列及其终值；同列已有值会被替换
     */
    void assign(Value value) {
        values.put(value.column().name(), value);
    }

    /**
     * 无字段变化时不取审计值，避免仅因保存动作就产生更新时间变化和无意义 DML。
     *
     * @param audit
     *            延迟提供审计列值的函数；没有变化时不会调用
     * @return 冻结后的字段及审计终值；无字段变化时为空
     */
    Map<String, Value> complete(Supplier<Map<String, Value>> audit) {
        return values.isEmpty() ? Map.of() : complete(audit, Map.of());
    }

    /**
     * 旧 NULL 版本按 0 递增，但 SQL 仍须匹配原始 NULL，不能将兼容默认值用于定位旧行。
     *
     * @param audit
     *            存在变化时提供审计列值的函数
     * @param expectedVersion
     *            原始 BIGINT 锁版本；null 按 0 递增，原值仍用于 SQL 定位
     * @return 含字段、下一版本及审计的不可修改终值；无变化时为空
     */
    Map<String, Value> complete(Supplier<Map<String, Value>> audit, Value expectedVersion) {
        if (values.isEmpty()) {
            return Map.of();
        }
        if (expectedVersion.column().jdbcType() != Types.BIGINT
                || (expectedVersion.value() != null && !(expectedVersion.value() instanceof Long))) {
            throw new IllegalArgumentException("锁版本必须为 BIGINT / Long");
        }
        long previous = expectedVersion.value() == null ? 0L : (Long) expectedVersion.value();
        Value next = new Value(expectedVersion.column(), Math.addExact(previous, 1L));
        return complete(audit, Map.of(next.column().name(), next));
    }

    /**
     * 合并业务终值与保留列，拒绝业务字段占用版本或审计列。
     *
     * @param audit
     *            提供本次审计列值的函数
     * @param version
     *            已计算好的版本列赋值；根行由 CAS 递增时传空映射
     * @return 合并并冻结后的全部列终值
     */
    private Map<String, Value> complete(Supplier<Map<String, Value>> audit, Map<String, Value> version) {
        Map<String, Value> result = new LinkedHashMap<>(values);
        appendReserved(result, version);
        appendReserved(result, audit.get());
        return JdbcWritePlan.immutable(result);
    }

    /**
     * 追加保留列并拒绝重复赋值，避免覆盖业务终值。
     *
     * @param result
     *            接收版本或审计值的可变终值映射
     * @param reserved
     *            待附加的版本或审计列值，列名必须与绑定列一致
     */
    private static void appendReserved(Map<String, Value> result, Map<String, Value> reserved) {
        JdbcWritePlan.immutable(reserved).forEach((name, value) -> {
            if (result.putIfAbsent(name, value) != null) {
                throw new IllegalStateException("业务终值与审计或版本列冲突: " + name);
            }
        });
    }
}
