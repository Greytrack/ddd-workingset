package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.CascadeMultiListValue;

import java.util.List;

/**
 * 级联多选校验策略：必填 + 逐项列表项存在且启用 + 从值落在主属性当前值允许集内。
 */
public final class CascadeMultiListValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.CASCADE_MULTIPLE_LIST;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        List<String> itemKeys = ((CascadeMultiListValue) value).itemKeys();
        for (String itemKey : itemKeys) {
            List<Violation> listViolations = AttributeValidationSupport.checkSingleList(itemKey, schema);
            if (!listViolations.isEmpty()) {
                return listViolations;
            }
        }
        return AttributeValidationSupport.checkCascadeRange(schema, itemKeys, mainAttributeValue);
    }
}
