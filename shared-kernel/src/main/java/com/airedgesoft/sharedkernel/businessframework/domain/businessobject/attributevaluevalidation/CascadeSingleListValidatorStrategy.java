package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.CascadeSingleListValue;

import java.util.List;

/**
 * 级联单选校验策略：必填 + 列表项存在且启用 + 从值落在主属性当前值允许集内。
 */
public final class CascadeSingleListValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.CASCADE_SINGLE_LIST;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        String itemKey = ((CascadeSingleListValue) value).itemKey();
        List<Violation> listViolations = AttributeValidationSupport.checkSingleList(itemKey, schema);
        if (!listViolations.isEmpty()) {
            return listViolations;
        }
        return AttributeValidationSupport.checkCascadeRange(schema, List.of(itemKey), mainAttributeValue);
    }
}
