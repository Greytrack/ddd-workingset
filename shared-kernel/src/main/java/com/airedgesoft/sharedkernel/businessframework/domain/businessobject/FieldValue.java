package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.RawValue;

/**
 * 值对象：属性字段（含值）。
 *
 * <p>
 * {@code record} 天然满足「按值相等 + 不可变」；{@code value} 恒为 {@link AttributeValue}（永不为 null，置空＝空实例）。
 *
 * <p>
 * <b>字段标识＝落库列名（2026-11 用户拍板）</b>：映射只认列名（旧栈分类元数据 {@code dbName}），故本值对象只持 column + value—— 原 {@code apiId}（协议/元数据标识）与
 * {@code type}（从未被读取的冗余分量）一并删除。业务数据（{@link BusinessDataCarrier}）的键也随之为列名； 协议 apiId ↔ 列名 的对照归各 BC 词表（如需求域
 * {@code RequirementAttributeSchema}），只在应用层入口/出口翻译。
 *
 * @param column
 *            落库列名（业务数据键）；不允许空白
 * @param value
 *            属性值（永不为 null）
 */
public record FieldValue(String column, AttributeValue value) {

    public FieldValue {
        if (column == null || column.isBlank()) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.FIELD_COLUMN_REQUIRED);
        }
    }

    /** 便捷构造：正式写入路径应传 {@link AttributeValue}。 */
    public static FieldValue of(String column, AttributeValue value) {
        return new FieldValue(column, value);
    }

    /** 迁移期便捷构造：裸值经 {@link RawValue} 兜底。 */
    public static FieldValue of(String column, Object value) {
        return new FieldValue(column,
                value instanceof AttributeValue attributeValue ? attributeValue : new RawValue(value));
    }
}
