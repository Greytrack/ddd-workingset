package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：级联单选（纯类型化字段，权威为 {@link #itemKey}；从值范围由策略跨字段判定）。
 */
public record CascadeSingleListValue(String itemKey) implements AttributeValue {

    public CascadeSingleListValue {
        itemKey = itemKey == null ? "" : itemKey;
    }

    /** 串形态构造（空值归一为空实例）。 */
    public static CascadeSingleListValue fromText(String text) {
        return text == null || text.isBlank() ? empty() : new CascadeSingleListValue(text.trim());
    }

    /** 空实例。 */
    public static CascadeSingleListValue empty() {
        return new CascadeSingleListValue("");
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.CASCADE_SINGLE_LIST;
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
