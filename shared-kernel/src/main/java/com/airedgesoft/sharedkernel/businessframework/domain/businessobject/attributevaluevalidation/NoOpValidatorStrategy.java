package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;

import java.util.List;

/**
 * 空实现校验策略（图标 / 虚拟 / 聚合三种，恒放行）。
 */
public final class NoOpValidatorStrategy implements AttributeValidatorStrategy {

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.VIRTUAL;
    }

    @Override
    public List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue) {
        return List.of();
    }
}
