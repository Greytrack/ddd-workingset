package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：组合文本（纯类型化字段，权威为 {@link #text} + {@link #itemKey}）。
 */
public record CompositeTextValue(String text, String itemKey) implements AttributeValue {

    /** 组合段分隔符（v3 {@code StringPool.COMPOSITE_TEXT_DELIMITER} 同值）。 */
    public static final String DELIMITER = "#s#";

    public CompositeTextValue {
        text = text == null ? "" : text;
        itemKey = itemKey == null ? "" : itemKey;
    }

    /** 串形态构造（空值归一为空实例；分隔段数 > 2 立即抛格式违例）。 */
    public static CompositeTextValue fromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        String[] parts = raw.split(DELIMITER, -1);
        if (parts.length > 2) {
            throw new AttributeValueFormatException(raw);
        }
        return new CompositeTextValue(parts[0], parts.length == 2 ? parts[1] : "");
    }

    /** 空实例。 */
    public static CompositeTextValue empty() {
        return new CompositeTextValue("", "");
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.COMPOSITE_TEXT;
    }

    @Override
    public boolean isEmpty() {
        return text.isBlank() && itemKey.isBlank();
    }

    @Override
    public String toText() {
        if (text.isBlank() && itemKey.isBlank()) {
            return "";
        }
        return itemKey.isBlank() ? text : text + DELIMITER + itemKey;
    }
}
