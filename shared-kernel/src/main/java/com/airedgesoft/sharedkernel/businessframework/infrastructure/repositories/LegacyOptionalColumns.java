package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories;

import com.airedgesoft.lib.jdbc.JdbcRowMapping;
import com.airedgesoft.sharedkernel.businessframework.application.command.OptionalFields;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionItem;
import com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject.ColumnValueConverter;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v3 共表「可选属性列」的列绑定机制：按 {@link OptionalFields} 的组件清单生成 {@link JdbcRowMapping.Binding}。
 *
 * <p>
 * <b>机制而非数据</b>：组件清单（150 列）来自命令契约，列→Java 类型按 v3 DDL 家族约定推出（{@code numeric*} 定点、 日期/列表族整型、其余文本）；
 */
public final class LegacyOptionalColumns {

    /** 定点数列前缀（v3 DDL 为 numeric）。 */
    private static final String DECIMAL_FAMILY_PREFIX = "numeric";

    /** 整型列族（v3 DDL 为 int8）：日期、起始/结束日期、列表。 */
    private static final java.util.Set<String> LONG_FAMILIES = java.util.Set.of("date", "startdate", "endstartdate",
            "list");

    /** 列名尾部序号（如 {@code date01}、{@code richtext001}）。 */
    private static final String INDEX_SUFFIX = "\\d{2,3}$";

    private LegacyOptionalColumns() {
    }

    /**
     * 可选列的列名 → Java 标量类型（按 v3 DDL 家族约定推导）。
     *
     * @return 不可变的列类型视图
     */
    public static Map<String, Class<?>> columnTypes() {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        for (RecordComponent component : OptionalFields.class.getRecordComponents()) {
            types.put(component.getName(), scalarTypeOf(component.getName()));
        }
        return Collections.unmodifiableMap(types);
    }

    /**
     * 由可选列清单生成版次行映射绑定（每列按标量类型取值）。
     *
     * @param columnTypes
     *            列名 → Java 标量类型
     * @return 可直接交给 {@link JdbcRowMapping#of} 的绑定
     */
    public static JdbcRowMapping.Binding<RevisionItem>[] bindings(Map<String, Class<?>> columnTypes) {
        @SuppressWarnings("unchecked")
        JdbcRowMapping.Binding<RevisionItem>[] bindings = new JdbcRowMapping.Binding[columnTypes.size()];
        int index = 0;
        for (Map.Entry<String, Class<?>> column : columnTypes.entrySet()) {
            bindings[index++] = binding(column.getKey(), column.getValue());
        }
        return bindings;
    }

    /** 单列绑定：按标量类型选择取值换算。 */
    private static JdbcRowMapping.Binding<RevisionItem> binding(String column, Class<?> type) {
        if (type == Long.class) {
            return JdbcRowMapping.column(column, Long.class, revision -> ColumnValueConverter.longOf(revision.getBusinessData(), column));
        }
        if (type == BigDecimal.class) {
            return JdbcRowMapping.column(column, BigDecimal.class,
                    revision -> ColumnValueConverter.decimalOf(revision.getBusinessData(), column));
        }
        return JdbcRowMapping.column(column, String.class,
                revision -> ColumnValueConverter.stringOf(revision.getBusinessData(), column));
    }


    /**
     * v3 口径：属性类型键 → 列 Java 标量。
     *
     * <p>
     * 照 v3 {@code AttributeTypeEnum} 每项自带的 {@code DataTypeEnum}（LONG/STRING/DOUBLE/BOOLEAN）与 DDL 类型串逐项对齐 ——
     * <b>按属性类型判定，不按列名猜</b>。元数据缺失时调用方再退回列名族推断（{@link #scalarTypeOf(String)}）。
     *
     * @param typeKey
     *            {@code AttributeFacts#typeKey()}（v3 {@code AttributeTypeEnum} 的 intKey）
     * @return 列 Java 标量；未知类型返回 {@code null}
     */
    public static Class<?> scalarTypeOfTypeKey(Integer typeKey) {
        if (typeKey == null) {
            return null;
        }
        return switch (typeKey) {
        // 数值 / 日期 / 开始结束日期：v3 DataTypeEnum.LONG（DDL float8、int8）
        case 3, 6, 9 -> Long.class;
        // 短文本 / 长文本 / 单选 / 多选 / 组合文本 / 组合数值 / 富文本 / 图片 / 标题 / 图标 / 虚拟 / 编号 / 引用 /
        // 表格 / 级联单选 / 级联多选 / 聚合：v3 DataTypeEnum.STRING
        case 1, 2, 4, 5, 7, 8, 10, 11, 13, 14, 15, 16, 19, 20, 21, 24, 25 -> String.class;
        default -> null;
        };
    }

    /** 列名 → 标量类型（v3 DDL 家族约定：{@code numeric*} 定点、日期/列表族整型、其余文本）。 */
    static Class<?> scalarTypeOf(String column) {
        if (column.startsWith(DECIMAL_FAMILY_PREFIX)) {
            return BigDecimal.class;
        }
        String family = column.replaceAll(INDEX_SUFFIX, "");
        return LONG_FAMILIES.contains(family) ? Long.class : String.class;
    }
}
