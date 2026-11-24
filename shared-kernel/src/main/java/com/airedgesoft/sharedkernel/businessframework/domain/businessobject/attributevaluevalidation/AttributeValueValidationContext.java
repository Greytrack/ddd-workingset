package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;

import java.util.List;
import java.util.Objects;

/**
 * 校验上下文：持有一个策略 ＋ 三份数据（值本体 / schema / 主属性值），对外只暴露 {@link #validate()} 转发。
 *
 * <p>
 * 每字段一个短命对象，不进聚合状态、不缓存；组装与「首条违例转异常」都在 {@link AttributeTypeFactory} 内完成。
 */
public final class AttributeValueValidationContext {

    private final AttributeValidatorStrategy strategy;

    private final AttributeValue value;

    private final AttributeFacts schema;

    private final AttributeValue mainAttributeValue;

    public AttributeValueValidationContext(AttributeValidatorStrategy strategy, AttributeValue value,
            AttributeFacts schema, AttributeValue mainAttributeValue) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.value = Objects.requireNonNull(value, "value");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.mainAttributeValue = mainAttributeValue;
    }

    /** 转发给策略执行校验。 */
    public List<Violation> validate() {
        return strategy.validate(value, schema, mainAttributeValue);
    }
}
