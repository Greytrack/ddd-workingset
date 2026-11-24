package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;

import java.util.List;

/**
 * 字段校验策略契约（GoF 策略模式）：无状态、按类型取单例复用，规则配置经 {@code schema} 入参传入。
 *
 * <p>
 * 依赖方向单向：Context → Strategy；策略吃数据参数（值本体 + schema + 主属性值），不认识 Context—— 可脱离 Context 单测。判定命中首条违例即由
 * {@link AttributeTypeFactory} 抛 {@code DomainException}。
 */
public interface AttributeValidatorStrategy {

    /** 策略对应的代表属性种类（文本族三种共用一个策略，工厂按 schema 类型键分派）。 */
    AttributeTypeEnum type();

    /**
     * 值校验。
     *
     * @param value
     *            被校验值（永不为 null；空值＝空实例）
     * @param schema
     *            属性元数据（必填 / 只读 / 规则配置）
     * @param mainAttributeValue
     *            主属性当前值（级联类跨字段判定用；非级联恒 null）
     * @return 违例列表（空即通过）
     */
    List<Violation> validate(AttributeValue value, AttributeFacts schema, AttributeValue mainAttributeValue);
}
