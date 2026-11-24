package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;
import com.airedgesoft.lib.exception.AttributeValueFormatException;
import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

/**
 * 值对象：开始结束日期（纯类型化字段，权威为 {@link #start} + {@link #end}）。
 */
public record StartAndEndDateValue(Long start, Long end) implements AttributeValue {

    /** 段分隔符。 */
    public static final String DELIMITER = "~";

    /** 串形态构造（空值归一为空实例；非毫秒值立即抛格式违例）。 */
    public static StartAndEndDateValue fromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        int index = raw.lastIndexOf(DELIMITER);
        String first = index < 0 ? raw : raw.substring(0, index);
        String second = index < 0 ? "" : raw.substring(index + DELIMITER.length());
        return new StartAndEndDateValue(parse(first, raw), parse(second, raw));
    }

    /** 空实例。 */
    public static StartAndEndDateValue empty() {
        return new StartAndEndDateValue(null, null);
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.START_AND_END_DATE;
    }

    @Override
    public boolean isEmpty() {
        return start == null && end == null;
    }

    @Override
    public String toText() {
        if (start == null && end == null) {
            return "";
        }
        return (start == null ? "" : start) + DELIMITER + (end == null ? "" : end);
    }

    private static Long parse(String value, String raw) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new AttributeValueFormatException(raw);
        }
    }
}
