package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 值对象：多选列表（纯类型化字段，权威为 {@link #itemKeys}）。
 */
public record MultipleListValue(List<String> itemKeys) implements AttributeValue {

    public MultipleListValue {
        itemKeys = itemKeys == null ? List.of() : List.copyOf(itemKeys);
    }

    /** 串形态构造（逗号分隔；空值归一为空实例；去重保序——v3 {@code createObjectBySubClassAndMap} 同口径）。 */
    public static MultipleListValue fromText(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String token : text.split(",")) {
            if (!token.isBlank()) {
                keys.add(token.trim());
            }
        }
        return new MultipleListValue(List.copyOf(keys));
    }

    /** 空实例。 */
    public static MultipleListValue empty() {
        return new MultipleListValue(List.of());
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.MULTIPLE_LIST;
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
