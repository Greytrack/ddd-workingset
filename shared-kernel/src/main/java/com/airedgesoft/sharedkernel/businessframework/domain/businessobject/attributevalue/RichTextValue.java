package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：富文本。
 *
 * <p>
 * 持 doc JSON 原始串：{@code fromText} 只做空值归一、{@code toText} 原样输出，不引 JSON 树、不解析语义。
 */
public record RichTextValue(String text) implements AttributeValue {

    public RichTextValue {
        text = text == null ? "" : text;
    }

    /** 串形态构造（空值归一为空实例）。 */
    public static RichTextValue fromText(String text) {
        return text == null || text.isBlank() ? empty() : new RichTextValue(text);
    }

    /** 空实例。 */
    public static RichTextValue empty() {
        return new RichTextValue("");
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.RICH_TEXT;
    }

    @Override
    public boolean isEmpty() {
        return text.isBlank();
    }

    @Override
    public String toText() {
        return text;
    }
}
