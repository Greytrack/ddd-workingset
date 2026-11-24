package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;
import com.airedgesoft.lib.exception.AttributeValueFormatException;
import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

/**
 * 值对象：日期（纯类型化字段，权威为 {@link #epochMilli}）。
 */
public record DateValue(Long epochMilli) implements AttributeValue {

    /** 串形态构造（空值归一为空实例；非毫秒值立即抛格式违例）。 */
    public static DateValue fromText(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        try {
            return new DateValue(Long.valueOf(text.trim()));
        } catch (NumberFormatException e) {
            throw new AttributeValueFormatException(text);
        }
    }

    /** 空实例。 */
    public static DateValue empty() {
        return new DateValue(null);
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.DATE;
    }

    @Override
    public boolean isEmpty() {
        return epochMilli == null;
    }

    @Override
    public String toText() {
        return epochMilli == null ? "" : String.valueOf(epochMilli);
    }
}
