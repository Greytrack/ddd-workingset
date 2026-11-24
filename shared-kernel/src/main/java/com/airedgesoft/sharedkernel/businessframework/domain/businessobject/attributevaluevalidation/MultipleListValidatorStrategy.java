package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.MultipleListValue;

import java.util.List;

/**
 * 多选列表校验策略：必填 + 逐项委托单选（遇首个违例即返）。
 */
public final class MultipleListValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.MULTIPLE_LIST;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        for (String itemKey : ((MultipleListValue) value).itemKeys()) {
            List<Violation> violations = AttributeValidationSupport.checkSingleList(itemKey, schema);
            if (!violations.isEmpty()) {
                return violations;
            }
        }
        return List.of();
    }
}
