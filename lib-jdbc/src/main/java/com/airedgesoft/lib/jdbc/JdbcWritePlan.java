package com.airedgesoft.lib.jdbc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.sql.Types;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固化编译后的操作顺序与标量值，避免执行阶段再读取可变领域对象，使校验结果与实际写入分离。
 *
 * <p>
 * {@link AggregateMapping.Plan} 还区分新增、更新和无变化，并为 Store 保留根 CAS 信息； 本类型只承载执行器消费的行操作，因此根 CAS 不属于此处的 operations。
 */
public record JdbcWritePlan(List<Operation> operations) {

    // JDBC 参数不能绑定表名和列名，因此标识符只接受受限格式，拒绝 SQL 片段。
    private static final String IDENTIFIER_PATTERN = "[a-z][a-z0-9_]*";
    /**
     * 冻结行操作顺序，防止执行前计划被调用方修改。
     *
     * @param operations
     *            按执行顺序排列的行操作，不包含由 Store 单独执行的根 CAS
     */
    public JdbcWritePlan {
        operations = List.copyOf(operations);
    }

    public record Table(String sqlName) {
        /**
         * 校验表名，避免 SQL 标识符携带任意片段。
         *
         * @param sqlName
         *            仅由小写字母开头、小写字母、数字和下划线组成的表名
         */
        public Table {
            identifier(sqlName);
        }
    }

    public record Column(String name, int jdbcType) {
        /**
         * 校验列名并保存显式 JDBC 类型。
         *
         * @param name
         *            符合受控标识符格式的数据库列名
         * @param jdbcType
         *            用于 JDBC 参数绑定的 java.sql.Types 类型值
         */
        public Column {
            identifier(name);
        }
        /**
         * 为已校验的列名添加 PostgreSQL 双引号。
         *
         * @return 可用于 SQL 的带双引号列名
         */
        public String quoted() {
            return "\"" + name + "\"";
        }
    }

    /** 仅允许不可变标量；SQL NULL 必须携带明确的 JDBC 类型。 */
    public record Value(Column column, Object value) {
        /**
         * 限制写入值为不可变标量，SQL NULL 也必须携带列类型。
         *
         * @param column
         *            非空列定义，包含列名和 JDBC 类型
         * @param value
         *            受支持的不可变数值、文本、Boolean、java.time、UUID 或表示 SQL NULL 的 null
         */
        public Value {
            if (column == null || (value != null && !(value instanceof String || value instanceof Long
                    || value instanceof Integer || value instanceof Double || value instanceof Boolean
                    || value instanceof Short || value instanceof Float || value instanceof BigDecimal
                    || value instanceof LocalDate || value instanceof LocalTime || value instanceof LocalDateTime
                    || value instanceof OffsetDateTime || value instanceof UUID))) {
                throw new IllegalArgumentException("未支持的持久化值类型或列");
            }
        }
    }

    /** 条件按 null-safe 等值组合；禁止空条件，避免无范围 UPDATE / DELETE。 */
    public record Target(Table table, Map<String, Value> predicates) {
        /**
         * 冻结非空行定位条件，避免生成无范围更新或删除。
         *
         * @param table
         *            非空目标表
         * @param predicates
         *            列名到绑定值的非空条件映射，执行时以 null-safe 等值条件连接
         */
        public Target {
            if (table == null || predicates.isEmpty()) {
                throw new IllegalArgumentException("持久化定位条件不能为空");
            }
            predicates = immutable(predicates);
        }
    }

    public sealed interface Operation {
    }
    public record Insert(Table table, Map<String, Value> values) implements Operation {
        /**
         * 冻结单行插入的列赋值，并拒绝空赋值。
         *
         * @param table
         *            插入目标表
         * @param values
         *            非空的插入列与值，包含调用方声明的身份及初始版本
         */
        public Insert {
            values = assignments(values);
        }
    }
    public record Change(Target target, Map<String, Value> values) implements Operation {
        /**
         * 冻结单行更新的终值，并拒绝空更新。
         *
         * @param target
         *            行定位条件，可包含原始锁版本
         * @param values
         *            非空的待更新列终值；定位条件独立保存
         */
        public Change {
            values = assignments(values);
        }
    }
    /** 交给数据库复制未覆盖列，避免应用未识别的扩展字段在重建再插入时丢失；生成列由执行器排除。 */
    public record Clone(Target source, Table target, Map<String, Value> overrides) implements Operation {
        /**
         * 冻结数据库行复制的覆盖值，未覆盖普通列由数据库直接复制。
         *
         * @param source
         *            复制来源定位条件，通常包含原始版本
         * @param target
         *            接收新行的目标表
         * @param overrides
         *            替换来源值的列赋值；可为空，生成列由执行器排除
         */
        public Clone {
            overrides = immutable(overrides);
        }
    }
    public record Delete(Target target) implements Operation {
    }

    /**
     * 校验列名与绑定列一致，并以稳定迭代顺序冻结映射。
     *
     * @param values
     *            待校验并复制的列名到绑定值映射
     * @return 保留输入迭代顺序的不可修改副本
     */
    public static Map<String, Value> immutable(Map<String, Value> values) {
        values.forEach((name, value) -> {
            if (!name.equals(value.column().name())) {
                throw new IllegalArgumentException("列映射不一致");
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * 构造带 BIGINT 类型的绑定值，保留 SQL NULL 的类型信息。
     *
     * @param column
     *            符合受控标识符格式的列名
     * @param value
     *            Long 列值；null 表示 SQL NULL
     * @return BIGINT 类型的列绑定值
     */
    public static Value bigint(String column, Long value) {
        return new Value(new Column(column, Types.BIGINT), value);
    }
    /**
     * 构造带 VARCHAR 类型的绑定值，保留 SQL NULL 的类型信息。
     *
     * @param column
     *            符合受控标识符格式的列名
     * @param value
     *            文本列值；null 表示 SQL NULL
     * @return VARCHAR 类型的列绑定值
     */
    public static Value text(String column, String value) {
        return new Value(new Column(column, Types.VARCHAR), value);
    }

    /**
     * 拒绝空赋值并冻结列值，保证 INSERT 和 UPDATE 有明确写入内容。
     *
     * @param values
     *            必须非空且列名与绑定列一致的赋值映射
     * @return 校验后的不可修改赋值副本
     */
    private static Map<String, Value> assignments(Map<String, Value> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("写入列不能为空");
        }
        return immutable(values);
    }

    /**
     * 限制 SQL 标识符格式，阻止把 SQL 片段作为表名或列名。
     *
     * @param name
     *            待校验的表名或列名；非法或 null 时抛出 IllegalArgumentException
     */
    private static void identifier(String name) {
        if (name == null || !name.matches(IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("非法持久化标识符");
        }
    }
}
