package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 值对象：级联多选（纯类型化字段，权威为 {@link #itemKeys}；从值范围由策略跨字段判定）。
 */
public record CascadeMultiListValue(List<String> itemKeys) implements AttributeValue {

    public CascadeMultiListValue {
        itemKeys = itemKeys == null ? List.of() : List.copyOf(itemKeys);
    }

    /** 串形态构造（逗号分隔；空值归一为空实例）。 */
    public static CascadeMultiListValue fromText(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        List<String> keys = new ArrayList<>();
        for (String token : text.split(",")) {
            if (!token.isBlank()) {
                keys.add(token.trim());
            }
        }
        return new CascadeMultiListValue(keys);
    }

    /** 空实例。 */
    public static CascadeMultiListValue empty() {
        return new CascadeMultiListValue(List.of());
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.CASCADE_MULTIPLE_LIST;
    }

    @Override
    public boolean isEmpty() {
        return itemKeys.isEmpty();
    }

    @Override
    public String toText() {
        return String.join(",", itemKeys);
    }
}
