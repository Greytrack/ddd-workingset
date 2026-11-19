package com.airedgesoft.lib.jdbc;

import com.airedgesoft.lib.jdbc.JdbcWritePlan.Column;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 将列类型与取值函数放在同一声明中，避免读取列、写入列和类型清单各自演进后不一致。
 *
 * <p>
 * 本类只处理一组列值；表名、行身份和锁版本由 {@link AggregateMapping.TableMapping} 声明， 使创建和更新可以复用字段定义，而不把创建期身份与初始版本误带进普通更新。
 *
 * @param <S>
 *            本组字段的取值来源，可以是聚合、子实体或仓储私有的创建上下文
 */
public final class JdbcRowMapping<S> {
    private static final Map<Class<?>, Integer> JDBC_TYPES = Map.ofEntries(Map.entry(String.class, Types.VARCHAR),
            Map.entry(Long.class, Types.BIGINT), Map.entry(Integer.class, Types.INTEGER),
            Map.entry(Double.class, Types.DOUBLE), Map.entry(Boolean.class, Types.BOOLEAN),
            Map.entry(Short.class, Types.SMALLINT), Map.entry(Float.class, Types.REAL),
            Map.entry(BigDecimal.class, Types.NUMERIC), Map.entry(LocalDate.class, Types.DATE),
            Map.entry(LocalTime.class, Types.TIME), Map.entry(LocalDateTime.class, Types.TIMESTAMP),
            Map.entry(OffsetDateTime.class, Types.TIMESTAMP_WITH_TIMEZONE), Map.entry(UUID.class, Types.OTHER));

    private final Map<String, Binding<S>> bindings;

    /**
     * 冻结字段声明并拒绝空映射和重复列，保证投影范围明确。
     *
     * @param fields
     *            按投影顺序排列的非空字段绑定列表
     */
    private JdbcRowMapping(List<Binding<S>> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("字段映射不能为空");
        }
        Map<String, Binding<S>> declared = new LinkedHashMap<>();
        for (Binding<S> field : fields) {
            String name = field.column.name();
            if (declared.putIfAbsent(name, field) != null) {
                throw new IllegalArgumentException("重复的映射列: " + name);
            }
        }
        bindings = Collections.unmodifiableMap(declared);
    }

    /**
     * 以一组有类型的字段绑定构造可复用的行映射。
     *
     * @param <S>
     *            字段取值来源类型
     * @param fields
     *            按声明顺序排列的字段绑定，不能为空或包含重复列
     * @return 不可变的字段映射
     */
    @SafeVarargs
    public static <S> JdbcRowMapping<S> of(Binding<S>... fields) {
        return new JdbcRowMapping<>(List.of(fields));
    }

    /**
     * 提供已声明列名，供仓储确定字段范围。
     *
     * @return 不可修改、按声明顺序迭代的列名集合
     */
    public Set<String> columnNames() {
        return bindings.keySet();
    }

    /**
     * 读写共用列定义，新增映射时无需另维护一份 SELECT 列清单。
     *
     * @return 按声明顺序排列的不可修改列定义，键为列名
     */
    public Map<String, Column> columns() {
        Map<String, Column> columns = new LinkedHashMap<>();
        bindings.forEach((name, field) -> columns.put(name, field.column));
        return Collections.unmodifiableMap(columns);
    }

    /**
     * 分组组合时仍检查重复列，避免后组静默覆盖前组；声明中的顺序保持稳定。
     *
     * @param other
     *            追加在当前字段组之后的映射，取值来源类型相同
     * @return 包含两组字段的新映射；当前映射保持不变
     */
    public JdbcRowMapping<S> and(JdbcRowMapping<S> other) {
        List<Binding<S>> combined = new ArrayList<>(bindings.values());
        combined.addAll(other.bindings.values());
        return new JdbcRowMapping<>(combined);
    }

    /**
     * 仅适配取值上下文，让创建和更新复用字段定义，避免列名或类型在两条路径上漂移。
     *
     * @param <T>
     *            适配后的外部取值来源类型
     * @param source
     *            将外部上下文转换为当前映射取值来源的函数
     * @return 从新上下文提取相同列值的映射
     */
    public <T> JdbcRowMapping<T> from(Function<T, S> source) {
        Objects.requireNonNull(source, "映射来源函数不能为空");
        List<Binding<T>> adapted = new ArrayList<>();
        bindings.values().forEach(field -> adapted.add(new Binding<>(field.column.name(), field.column.jdbcType(),
                row -> field.extractor.apply(source.apply(row)))));
        return new JdbcRowMapping<>(adapted);
    }

    /**
     * 显式 NULL 仍保留列和 JDBC 类型，避免将“清空字段”与“没有装配字段”混为一谈。
     *
     * @param source
     *            非空的聚合、子实体或仓储取值上下文
     * @return 按声明顺序排列的不可修改列值，包含 JDBC 类型和显式 null
     */
    public Map<String, Value> project(S source) {
        Objects.requireNonNull(source, "映射来源不能为空");
        Map<String, Value> values = new LinkedHashMap<>();
        bindings.forEach((name, field) -> values.put(name, new Value(field.column, field.extractor.apply(source))));
        return JdbcWritePlan.immutable(values);
    }

    /**
     * 显式声明列名、Java 值类型和取值函数，统一推导读取与写入所用的 JDBC 类型。
     *
     * @param <S>
     *            字段取值来源类型
     * @param <T>
     *            列值类型，与取值函数返回类型保持一致
     * @param name
     *            受控的数据库列名
     * @param type
     *            受支持的不可变标量类型（含 BigDecimal、java.time 和 UUID），不接受基本类型
     * @param extractor
     *            从来源对象提取列值的函数；返回 null 表示 SQL NULL
     * @return 绑定列类型与取值函数的字段声明，声明时不执行取值函数
     * @throws IllegalArgumentException
     *             列名非法或 Java 类型不受支持
     * @throws NullPointerException
     *             类型或取值函数为空
     */
    public static <S, T> Binding<S> column(String name, Class<T> type, Function<S, T> extractor) {
        Objects.requireNonNull(type, "列值类型不能为空");
        Integer jdbcType = JDBC_TYPES.get(type);
        if (jdbcType == null) {
            throw new IllegalArgumentException("未支持的列值类型: " + type.getTypeName());
        }
        return new Binding<>(name, jdbcType, extractor);
    }

    /** 通过有类型的工厂构造，避免声明的 JDBC 类型与取值函数返回类型分离。 */
    public static final class Binding<S> {
        private final Column column;
        private final Function<S, ?> extractor;

        /**
         * 将列类型与取值函数绑定，供有类型的工厂方法复用。
         *
         * @param name
         *            受控的数据库列名
         * @param jdbcType
         *            参数绑定和读取共用的 JDBC 类型
         * @param extractor
         *            从来源对象提取该列标量值的非空函数
         */
        private Binding(String name, int jdbcType, Function<S, ?> extractor) {
            column = new Column(name, jdbcType);
            this.extractor = Objects.requireNonNull(extractor, "字段取值函数不能为空");
        }
    }
}
