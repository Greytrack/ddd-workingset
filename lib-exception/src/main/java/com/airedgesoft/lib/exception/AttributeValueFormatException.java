package com.airedgesoft.lib.exception;

/**
 * 属性值格式非法（技术性解析信号，非业务语义）。
 *
 * <p>
 * 由各属性值对象 {@code fromText} 抛出，消费方（如 shared-kernel 的 {@code AttributeTypeFactory}）捕获后转为带字段名的
 * {@link DomainException}（{@code ATTRIBUTE_VALUE_FORMAT_INVALID}）；值对象拿不到 schema，故字段名由消费方补齐。
 *
 * <p>
 * 归属 lib-exception 的例外：本异常仅携带解析失败原文，不携带任何业务字段（原 v3 属性类型枚举随迁移剥离）， 因此不构成业务语义，永不映射 4xx（规范 §7.4 D2 技术性异常口径）。
 */
public class AttributeValueFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String raw;

    public AttributeValueFormatException(String raw) {
        super("属性值格式非法: " + raw);
        this.raw = raw;
    }

    public String raw() {
        return raw;
    }
}
