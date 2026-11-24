package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.lib.exception.AttributeValueFormatException;
import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.*;

import java.util.List;

/**
 * 类型 → 实现族映射工厂（共享）。
 *
 * <p>
 * 全系统唯一「类型键 → 实现族」映射处：{@link #createAttributeValue} 派到值类 {@code fromText} 并把「串 → 值」与「值校验」合成一次调用；
 * {@link #createAttributeValidatorStrategy} 按种类取策略单例。两者共用同一张类型映射；消费点不写枚举 switch、不自行拆串。
 *
 * <p>
 * 校验口径（doc 4 决策 7）：空值也进工厂（{@code text} 为 null/空白 → 该类型 {@code empty()}，必填由策略判）； 命中首条违例即抛
 * {@link DomainException}（不收集）；返回的值即「已通过校验」的值。
 */
public final class AttributeTypeFactory {

    private AttributeTypeFactory() {
    }

    /**
     * 生成属性值（转换 + 校验合一）。
     *
     * @param schema
     *            属性元数据（类型键 / 必填 / 规则配置）
     * @param text
     *            串形态入参（null / 空白 → 空实例）
     * @param mainAttributeValue
     *            主属性当前值（级联类跨字段判定用；非级联恒 null）
     * @return 已通过校验的属性值
     * @throws DomainException
     *             命中首条校验违例
     */
    public static AttributeValue createAttributeValue(AttributeFacts schema, String text,
            AttributeValue mainAttributeValue) {
        AttributeTypeEnum type = typeOf(schema);
        AttributeValue value;
        try {
            value = parse(type, text);
        } catch (AttributeValueFormatException e) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.ATTRIBUTE_VALUE_FORMAT_INVALID, schema.name(), e.raw());
        }
        AttributeValidatorStrategy strategy = createAttributeValidatorStrategy(type);
        List<Violation> violations = new AttributeValueValidationContext(strategy, value, schema, mainAttributeValue)
                .validate();
        if (!violations.isEmpty()) {
            Violation first = violations.get(0);
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, first.code(), first.args());
        }
        return value;
    }

    /** 按种类取策略单例（选策略唯一入口，消费点零 switch）。 */
    public static AttributeValidatorStrategy createAttributeValidatorStrategy(AttributeTypeEnum type) {
        return switch (type) {
            case SHORT_TEXT, LONG_TEXT, TITLE -> new TextValidatorStrategy();
            case RICH_TEXT -> new RichTextValidatorStrategy();
            case NUMERICAL_VALUE -> new NumericalValidatorStrategy();
            case SINGLE_LIST -> new SingleListValidatorStrategy();
            case MULTIPLE_LIST -> new MultipleListValidatorStrategy();
            case DATE -> new DateValidatorStrategy();
            case COMPOSITE_TEXT -> new CompositeTextValidatorStrategy();
            case COMPOSITE_NUMERICAL_VALUE -> new CompositeNumericValidatorStrategy();
            case START_AND_END_DATE -> new StartAndEndDateValidatorStrategy();
            case IMAGES -> new ImagesValidatorStrategy();
            case NUMBER -> new NumberValidatorStrategy();
            case CASCADE_SINGLE_LIST -> new CascadeSingleListValidatorStrategy();
            case CASCADE_MULTIPLE_LIST -> new CascadeMultiListValidatorStrategy();
            case ICON, VIRTUAL, AGGREGATION -> new NoOpValidatorStrategy();
            default -> throw new DomainException(SharedKernelResultCode.ATTRIBUTE_TYPE_UNSUPPORTED, type);
        };
    }

    /** 属性类型键（schema.attributeType 的原语义键）。 */
    public static AttributeTypeEnum typeOf(AttributeFacts schema) {
        return AttributeTypeEnum.of(Long.valueOf(schema.typeKey()));
    }

    private static AttributeValue parse(AttributeTypeEnum type, String text) {
        return switch (type) {
            case SHORT_TEXT, LONG_TEXT, TITLE -> TextValue.fromText(type, text);
            case RICH_TEXT -> RichTextValue.fromText(text);
            case NUMERICAL_VALUE -> NumericalValue.fromText(text);
            case SINGLE_LIST -> SingleListValue.fromText(text);
            case MULTIPLE_LIST -> MultipleListValue.fromText(text);
            case DATE -> DateValue.fromText(text);
            case COMPOSITE_TEXT -> CompositeTextValue.fromText(text);
            case COMPOSITE_NUMERICAL_VALUE -> CompositeNumericValue.fromText(text);
            case START_AND_END_DATE -> StartAndEndDateValue.fromText(text);
            case IMAGES -> ImagesValue.fromText(text);
            case NUMBER -> NumberValue.fromText(text);
            case CASCADE_SINGLE_LIST -> CascadeSingleListValue.fromText(text);
            case CASCADE_MULTIPLE_LIST -> CascadeMultiListValue.fromText(text);
            case ICON, VIRTUAL, AGGREGATION -> NoOpValue.fromText(type, text);
            default -> throw new DomainException(SharedKernelResultCode.ATTRIBUTE_TYPE_UNSUPPORTED, type);
        };
    }
}
