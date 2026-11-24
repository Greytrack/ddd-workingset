package com.airedgesoft.lib.restful.filter;

import java.util.List;

/**
 * {@link FilterAst} → 持久层适配器值的下推转换接口（v4-restful-api.md §7 filter 行；Tier B「过滤 AST 转换」）。
 *
 * <p>
 * 适配器（MyBatis-Plus 读 Provider / JDBC 读仓储）实现本接口：{@code visitLogical} 组合子条件、{@code visitComparison} 产出 「列 + 操作符 +
 * 值」三元组、visit* 产出操作数值形态（数字建议 {@link java.math.BigDecimal} 后按列类型收窄）。翻译在 infrastructure/adapters 层进行——应用层 Query 只携带原始
 * filter 字符串或已解析 AST（纯数据）。
 *
 * @param <T>
 *            目标条件形态（如 MP {@code Wrapper} 片段、SQL 片段 + 参数列表的组合对象）
 */
public interface FilterTranslator<T> {

    /** 逻辑组合（AND / OR；children 已递归翻译）。 */
    T visitLogical(FilterAst.Logical.Kind kind, List<T> children);

    /** 一元否定（NOT / -；child 已递归翻译，AIP-160:73-75）。 */
    T visitNot(T child);

    /** 比较下推（field 为白名单域字段路径，value 为操作数值）。 */
    T visitComparison(String field, FilterAst.Operator operator, T value);

    /** 字符串字面量。 */
    T visitText(String value);

    /** 数字字面量。 */
    T visitNumber(java.math.BigDecimal value);

    /** 布尔字面量。 */
    T visitBoolean(boolean value);

    /** {@code null} 字面量。 */
    T visitNull();

    /** 字段引用（字段间比较）。 */
    T visitFieldRef(String field);
}
