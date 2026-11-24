package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.CompositeNumericValue;

import java.util.List;

/**
 * 组合数值校验策略：必填 + 前段数值（含前段必填）+ 后段单选。
 */
public final class CompositeNumericValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.COMPOSITE_NUMERICAL_VALUE;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        CompositeNumericValue composite = (CompositeNumericValue) value;
        if (composite.value() == null && AttributeValidationSupport.required(schema)) {
            return AttributeValidationSupport.requiredViolation(schema);
        }
        List<Violation> numericalViolations = AttributeValidationSupport.checkNumerical(composite.value(), schema);
        if (!numericalViolations.isEmpty()) {
            return numericalViolations;
        }
        return AttributeValidationSupport.checkSingleList(composite.itemKey(), schema);
    }
}
