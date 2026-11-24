package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：组合数值（纯类型化字段，权威为 {@link #value} + {@link #itemKey}）。
 */
public record CompositeNumericValue(Double value, String itemKey) implements AttributeValue {

    public CompositeNumericValue {
        itemKey = itemKey == null ? "" : itemKey;
    }

    /** 串形态构造（空值归一为空实例；前段非数值立即抛格式违例）。 */
    public static CompositeNumericValue fromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        String[] parts = raw.split(CompositeTextValue.DELIMITER, -1);
        if (parts.length > 2) {
            throw new AttributeValueFormatException(raw);
        }
        Double numeric = null;
        if (!parts[0].isBlank()) {
            try {
                numeric = Double.valueOf(parts[0].trim());
            } catch (NumberFormatException e) {
                throw new AttributeValueFormatException(raw);
            }
        }
        return new CompositeNumericValue(numeric, parts.length == 2 ? parts[1] : "");
    }

    /** 空实例。 */
    public static CompositeNumericValue empty() {
        return new CompositeNumericValue(null, "");
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.COMPOSITE_NUMERICAL_VALUE;
    }

    @Override
    public boolean isEmpty() {
        return value == null && itemKey.isBlank();
    }

    @Override
    public String toText() {
        if (value == null && itemKey.isBlank()) {
            return "";
        }
        String first = value == null ? "" : NumericalValue.format(value);
        return itemKey.isBlank() ? first : first + CompositeTextValue.DELIMITER + itemKey;
    }
}
