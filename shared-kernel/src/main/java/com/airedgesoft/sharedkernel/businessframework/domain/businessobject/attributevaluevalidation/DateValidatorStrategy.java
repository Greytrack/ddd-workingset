package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.DateValue;

import java.util.List;

/**
 * 日期校验策略：必填 + 日期规则命中性（格式在 {@link DateValue#fromText} 已判）。
 */
public final class DateValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.DATE;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        return AttributeValidationSupport.checkDate(((DateValue) value).epochMilli(), schema);
    }
}
