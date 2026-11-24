package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.lib.exception.AttributeValueFormatException;
import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

import java.math.BigDecimal;

/**
 * 值对象：数值（纯类型化字段，权威为 {@link #value}）。
 */
public record NumericalValue(Double value) implements AttributeValue {

    /** 串形态构造（空值归一为空实例；非数值立即抛格式违例）。 */
    public static NumericalValue fromText(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        try {
            return new NumericalValue(Double.valueOf(text.trim()));
        } catch (NumberFormatException e) {
            throw new AttributeValueFormatException(text);
        }
    }

    /** 空实例。 */
    public static NumericalValue empty() {
        return new NumericalValue(null);
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.NUMERICAL_VALUE;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public String toText() {
        return value == null ? "" : format(value);
    }

    /** 数值串归一（去尾零，避免 "1" 变 "1.0"）。 */
    public static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
