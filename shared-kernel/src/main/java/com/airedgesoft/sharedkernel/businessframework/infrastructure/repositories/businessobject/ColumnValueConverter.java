package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import com.airedgesoft.lib.jdbc.StoredRow;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.RawValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 列值 ↔ 字段值的类型转换（v3 共表口径）：空白/空值一律 {@code null}，不做任何业务判断。
 *
 * <p>
 * 换算只看<b>目标列类型</b>，不看业务含义——所以它对所有 v3 共表 BC 一致，不留在各 BC 的 Mapper 里重复实现。
 *
 * @see RevisionRepositorySupport.Protocol
 */
public final class ColumnValueConverter {

    private ColumnValueConverter() {
    }

    /**
     * 取整型的列值。
     *
     * @param fields
     *            字段（键＝落库列名）
     * @param column
     *            列名
     * @return 整型值；缺失/空白返回 {@code null}
     */
    public static Long longOf(Map<String, FieldValue> fields, String column) {
        String text = textOf(fields, column);
        return text == null || text.isBlank() ? null : Long.valueOf(text.trim());
    }

    /**
     * 取文本列值（不做 trim：富文本/描述类内容需保真）。
     *
     * @param fields
     *            字段
     * @param column
     *            列名
     * @return 文本；缺失或空值返回 {@code null}
     */
    public static String stringOf(Map<String, FieldValue> fields, String column) {
        FieldValue fieldValue = fields.get(column);
        return fieldValue == null || fieldValue.value() == null || fieldValue.value().isEmpty()
                ? null
                : fieldValue.value().toText();
    }

    /**
     * 取浮点列值。
     *
     * @param fields
     *            字段
     * @param column
     *            列名
     * @return 浮点值；缺失/空白返回 {@code null}
     */
    public static Double doubleOf(Map<String, FieldValue> fields, String column) {
        String text = textOf(fields, column);
        return text == null || text.isBlank() ? null : Double.valueOf(text.trim());
    }

    /**
     * 取定点数列值（numeric）：用 {@code BigDecimal} 保证精度，不做浮点近似。
     *
     * @param fields
     *            字段
     * @param column
     *            列名
     * @return 定点值；缺失/空白返回 {@code null}
     */
    public static BigDecimal decimalOf(Map<String, FieldValue> fields, String column) {
        String text = textOf(fields, column);
        return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
    }

    /**
     * 可选列的字段值：列在行里有非 NULL 值才装入（NULL 视为「未加载」，重建时不参与、也不会被误判为清空）。
     *
     * @param row
     *            原始行
     * @param columns
     *            可选列清单
     * @return 字段值列表（按清单顺序）
     */
    public static List<FieldValue> optionalValuesOf(StoredRow row, Collection<String> columns) {
        List<FieldValue> fieldValues = new ArrayList<>(columns.size());
        for (String column : columns) {
            Object value = row.values().get(column);
            if (value != null) {
                fieldValues.add(FieldValue.of(column, new RawValue(value)));
            }
        }
        return fieldValues;
    }

    /** 取列值的文本形式（供各标量换算共用；缺失/空值返回 {@code null}）。 */
    private static String textOf(Map<String, FieldValue> fields, String column) {
        FieldValue fieldValue = fields.get(column);
        return fieldValue == null || fieldValue.value() == null || fieldValue.value().isEmpty()
                ? null
                : fieldValue.value().toText();
    }
}
