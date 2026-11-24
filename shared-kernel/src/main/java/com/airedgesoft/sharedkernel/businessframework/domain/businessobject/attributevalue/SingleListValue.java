package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;
import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

/**
 * 值对象：单选列表（纯类型化字段，权威为 {@link #itemKey}）。
 *
 * <p>
 * 列表项 key 可为数字 id 或 apiId 串（v3 口径），故按 {@code String} 携带。
 */
public record SingleListValue(String itemKey) implements AttributeValue {

    public SingleListValue {
        itemKey = itemKey == null ? "" : itemKey;
    }

    /** 串形态构造（空值归一为空实例）。 */
    public static SingleListValue fromText(String text) {
        return text == null || text.isBlank() ? empty() : new SingleListValue(text.trim());
    }

    /** 空实例。 */
    public static SingleListValue empty() {
        return new SingleListValue("");
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.SINGLE_LIST;
    }

    @Override
    public boolean isEmpty() {
        return itemKey.isBlank();
    }

    @Override
    public String toText() {
        return itemKey;
    }
}
