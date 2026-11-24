package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：文本（短文本 / 长文本 / 标题三种共用）。
 *
 * <p>
 * 三种种类共用本类，故 {@link #type()} 须由构造传入（工厂按 schema 类型键传入），空实例默认短文本。
 */
public record TextValue(AttributeTypeEnum type, String text) implements AttributeValue {

    public TextValue {
        type = type == null ? AttributeTypeEnum.SHORT_TEXT : type;
        text = text == null ? "" : text;
    }

    /** 串形态构造（默认短文本种类）。 */
    public static TextValue fromText(String text) {
        return new TextValue(AttributeTypeEnum.SHORT_TEXT, normalize(text));
    }

    /** 串形态构造（指定种类，空值归一为空实例）。 */
    public static TextValue fromText(AttributeTypeEnum type, String text) {
        return isBlank(text) ? empty(type) : new TextValue(type, text);
    }

    /** 空实例（默认短文本种类）。 */
    public static TextValue empty() {
        return new TextValue(AttributeTypeEnum.SHORT_TEXT, "");
    }

    /** 空实例（指定种类）。 */
    public static TextValue empty(AttributeTypeEnum type) {
        return new TextValue(type, "");
    }

    @Override
    public boolean isEmpty() {
        return text.isBlank();
    }

    @Override
    public String toText() {
        return text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
