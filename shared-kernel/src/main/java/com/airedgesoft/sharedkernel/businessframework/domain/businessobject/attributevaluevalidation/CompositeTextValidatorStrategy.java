package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.CompositeTextValue;

import java.util.List;

/**
 * 组合文本校验策略：必填 + 前段文本（含前段必填）+ 后段单选。
 */
public final class CompositeTextValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.COMPOSITE_TEXT;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        CompositeTextValue composite = (CompositeTextValue) value;
        if (composite.text().isBlank() && AttributeValidationSupport.required(schema)) {
            return AttributeValidationSupport.requiredViolation(schema);
        }
        List<Violation> textViolations = AttributeValidationSupport.checkText(composite.text(), schema);
        if (!textViolations.isEmpty()) {
            return textViolations;
        }
        return AttributeValidationSupport.checkSingleList(composite.itemKey(), schema);
    }
}
