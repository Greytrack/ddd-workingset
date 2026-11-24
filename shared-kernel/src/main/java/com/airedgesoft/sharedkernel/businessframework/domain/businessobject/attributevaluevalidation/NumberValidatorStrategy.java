package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.NumberValue;

import java.util.List;

/**
 * 编号校验策略：必填 + 文本规则（手输号串按文本口径校验；段配置 / 引擎形态由编号引擎生成，不经本策略——应用层手输号经 {@code NumberValue#fromPlainText} 承载才落进本策略口径）。
 */
public final class NumberValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.NUMBER;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (value.isEmpty()) {
            return AttributeValidationSupport.required(schema)
                    ? AttributeValidationSupport.requiredViolation(schema)
                    : List.of();
        }
        NumberValue number = (NumberValue) value;
        return AttributeValidationSupport.checkText(number.isPlainText() ? number.toText() : "", schema);
    }
}
