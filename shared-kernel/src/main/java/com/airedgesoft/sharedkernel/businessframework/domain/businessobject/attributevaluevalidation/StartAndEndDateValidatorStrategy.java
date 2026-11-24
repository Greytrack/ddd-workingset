package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.StartAndEndDateValue;

import java.util.List;

/**
 * 开始结束日期校验策略：必填 + 开始 ≤ 结束 + 开始段日期规则。
 */
public final class StartAndEndDateValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.START_AND_END_DATE;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        StartAndEndDateValue date = (StartAndEndDateValue) value;
        List<Violation> orderViolation = AttributeValidationSupport.checkStartNotAfterEnd(date.start(), date.end(),
                schema);
        if (!orderViolation.isEmpty()) {
            return orderViolation;
        }
        return AttributeValidationSupport.checkDate(date.start(), schema);
    }
}
