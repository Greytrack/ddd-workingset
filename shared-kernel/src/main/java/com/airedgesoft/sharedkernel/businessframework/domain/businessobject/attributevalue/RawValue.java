package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：原始值（迁移期占位）。
 *
 * <p>
 * 承载无属性元数据的历史 / 扩展列裸值（如版次预留字段的 {@code Long/String/BigDecimal} 写入），使 {@code FieldValue.value} 得以恒为
 * {@link AttributeValue}。⚠️ 待预留字段按动态属性元数据接入后下线，届时所有字段值都应有具体属性种类。
 */
public record RawValue(Object value) implements AttributeValue {

    @Override
    public AttributeTypeEnum type() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public String toText() {
        return value == null ? "" : String.valueOf(value);
    }
}
