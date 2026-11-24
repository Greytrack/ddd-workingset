package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.RawValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionItem;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 接口：业务数据承载者（BusinessDataCarrier）——「数据对象」的能力契约：以「属性字段集合」承载业务数据的泛属性容器。
 *
 * <p>
 * 真实业务对象（{@link BusinessObject}）与聚合内实体（{@link RevisionItem}）都继承本契约；「是不是业务对象」（身份）与「能不能承载业务数据」（能力）彻底解耦。
 *
 * <p>
 * <b>键＝落库列名（2026-11 用户拍板）</b>：映射只认列名，故业务数据的键是 {@link FieldValue#column()}，取/写入口一律按列名寻址； 协议 apiId ↔ 列名 的翻译归各 BC 词表 +
 * 应用层（领域不认识协议标识）。
 *
 * <p>
 * {@code value} 恒为 {@link AttributeValue}；无元数据的扩展列裸值经 {@link RawValue} 兜底（迁移期）。
 */
public interface BusinessDataCarrier {

    /** SPI：业务数据（键＝落库列名；写入保序、同列覆盖）。 */
    Map<String, FieldValue> getBusinessData();

    /** 写入单个字段值对象（同列覆盖）。 */
    default void setFieldValue(FieldValue fieldValue) {
        Objects.requireNonNull(fieldValue, "fieldValue");
        getBusinessData().put(fieldValue.column(), fieldValue);
    }

    /** 批量写入字段（同列覆盖）。 */
    default void setFieldValues(Collection<FieldValue> values) {
        if (values == null) {
            return;
        }
        values.forEach(this::setFieldValue);
    }

    /** 写入单个已校验属性值（按列名）。 */
    default void setFieldValue(String column, AttributeValue value) {
        setFieldValue(FieldValue.of(column, value));
    }

    /** 写入单个原始值（无属性元数据口径，迁移期兜底）。 */
    default void setFieldValue(String column, Object value) {
        setFieldValue(column, value instanceof AttributeValue attributeValue ? attributeValue : new RawValue(value));
    }

    /** 取属性值（按列名）；不存在返回 null。 */
    default AttributeValue getValue(String column) {
        FieldValue fieldValue = getBusinessData().get(column);
        return fieldValue == null ? null : fieldValue.value();
    }

    /** 全部字段（只读视图，保持写入顺序）。 */
    default Map<String, FieldValue> getAllFields() {
        return Collections.unmodifiableMap(getBusinessData());
    }

    /** 类型化读：长整型（不可解析抛 {@link DomainException}）。 */
    default Long getLongValue(String column) {
        String text = textOf(column);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.FIELD_NOT_NUMBER, e,
                    column, text);
        }
    }

    /** 类型化读：整型。 */
    default Integer getIntegerValue(String column) {
        String text = textOf(column);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.FIELD_NOT_INTEGER, e,
                    column, text);
        }
    }

    /** 类型化读：双精度。 */
    default Double getDoubleValue(String column) {
        String text = textOf(column);
        if (text == null) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.FIELD_NOT_NUMBER, e,
                    column, text);
        }
    }

    /** 类型化读：文本。 */
    default String getStringValue(String column) {
        AttributeValue value = getValue(column);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.toText();
    }

    /** 类型化读：数值精度列（numeric）。 */
    default BigDecimal getBigDecimalValue(String column) {
        String text = textOf(column);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.FIELD_NOT_NUMBER, e,
                    column, text);
        }
    }

    private String textOf(String column) {
        AttributeValue value = getValue(column);
        return value == null || value.isEmpty() ? null : value.toText();
    }
}
