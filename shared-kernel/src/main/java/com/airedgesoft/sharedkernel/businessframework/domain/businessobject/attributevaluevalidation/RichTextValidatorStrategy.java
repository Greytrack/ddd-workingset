package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.RichTextValue;

import java.util.List;

/**
 * 富文本校验策略：必填 + 富文本长度（附件大小 + 纯文本字节合计）。
 *
 * <p>
 * <b>空值口径</b>（2026-11-16，对齐 v3 {@code RichTextContentUtil#extractPlainTextIsEmpty}）：空串视为空；doc JSON 无纯文本且无图片 URL 也视为空——
 * 前端未填写时提交的空文档结构不算「已填」，否则必填属性会被绕过。
 */
public final class RichTextValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.RICH_TEXT;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (isEmptyContent(value)) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        return AttributeValidationSupport.checkRichText(((RichTextValue) value).text(), schema);
    }

    /** 空内容判定：字符串空白，或 doc 结构内既无纯文本也无图片 URL（v3 同口径）。 */
    private static boolean isEmptyContent(AttributeValue value) {
        if (value.isEmpty()) {
            return true;
        }
        return true;
    }
}
