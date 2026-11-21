package com.airedgesoft.lib.jdbc;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 存储行：按列声明读取、尚未施加领域默认值的不可变标量集合，用于替代每个聚合专属的 PO。
 *
 * <p>
 * 列集合允许因表而异，因此使用 Map 承载；类型由读取声明和有类型访问方法约束，不允许任意可变对象。 这里只保留已声明的读取列，未知数据库列由数据库 Clone 保留，不把“未读取”伪装成 NULL。
 */
public record StoredRow(Map<String, Object> values) {
    /**
     * 复制并冻结已读取的列值，只允许不可变标量和 SQL NULL。
     *
     * @param values
     *            以列名为键的原始存储值；保留 null 和列是否已加载的区别
     */
    public StoredRow {
        // Map.copyOf 不允许 SQL NULL；显式拷贝后只读包装，既保留 NULL，又隔离调用方对原 Map 的修改。
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        values.forEach((name, value) -> {
            // 复用公共列标识符校验；OTHER 仅用于校验名称，不推断或替换真实 JDBC 类型。
            new JdbcWritePlan.Column(name, Types.OTHER);
            // 只读 Map 无法冻结可变的内部值；限制为不可变标量，才不会在加载后被间接篡改。
            if (value != null && !(value instanceof String || value instanceof Long || value instanceof Integer
                    || value instanceof Double || value instanceof Boolean || value instanceof Short
                    || value instanceof Float || value instanceof BigDecimal || value instanceof LocalDate
                    || value instanceof LocalTime || value instanceof LocalDateTime || value instanceof OffsetDateTime
                    || value instanceof UUID)) {
                throw new IllegalArgumentException("不支持的快照值类型: " + name);
            }
        });
    }

    /**
     * 缺席列必须报错：否则映射遗漏会被当成 NULL，保存时可能误清空已有值。
     *
     * @param <T>
     *            期望的列值类型
     * @param column
     *            必须已加载的列名
     * @param type
     *            用于运行时类型检查的目标类型
     * @return 转换后的列值；SQL NULL 返回 null，缺席列或类型不匹配时抛出异常
     */
    public <T> T get(String column, Class<T> type) {
        if (!values.containsKey(column)) {
            throw new IllegalStateException("列未加载: " + column);
        }
        // 不做字符串猜测或宽松强转，使调用方的类型错误在重建处暴露。
        return type.cast(values.get(column));
    }

    /**
     * 按 Long 读取列值，不做宽松数值转换。
     *
     * @param column
     *            已加载的 BIGINT 列名
     * @return Long 列值，SQL NULL 返回 null
     */
    public Long longValue(String column) {
        return get(column, Long.class);
    }
    /**
     * 兼容旧库 BIGINT 标志值；使用精确收窄，避免溢出后把版次号或状态解释成另一个合法值。
     *
     * @param column
     *            已加载的整数列名
     * @return Integer 列值，SQL NULL 返回 null；Long 超出 int 范围时抛出 ArithmeticException
     */
    public Integer integerValue(String column) {
        Object value = values.get(column);
        if (value instanceof Long number)
            return Math.toIntExact(number);
        return get(column, Integer.class);
    }
    /**
     * 按 String 读取已加载的文本列。
     *
     * @param column
     *            已加载的文本列名
     * @return 文本值，SQL NULL 返回 null
     */
    public String textValue(String column) {
        return get(column, String.class);
    }
    /**
     * 按 Double 读取已加载的浮点列。
     *
     * @param column
     *            已加载的浮点列名
     * @return Double 列值，SQL NULL 返回 null
     */
    public Double doubleValue(String column) {
        return get(column, Double.class);
    }

}
