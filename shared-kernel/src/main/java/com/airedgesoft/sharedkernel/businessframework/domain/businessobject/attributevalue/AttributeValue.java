package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;
import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;

/**
 * 属性值契约：全部属性种类（14 族）的携带值对象统一实现本接口。
 *
 * <p>
 * 三方法一职：{@link #type()} 回种类、{@link #isEmpty()} 回空、{@link #toText()} 出串； 判定不在值对象上（归 {@code XxxValidatorStrategy}），故本接口无
 * {@code validate}。
 *
 * <p>
 * {@code fromText}（串 → 值）与 {@code empty}（空实例）是构造动作，不进接口（接口静态方法不可被实现类覆写），留各实现类静态工厂。 {@code FieldValue.value} 永不为
 * null，置空＝空实例。
 */
public interface AttributeValue {

    /** 属性种类（v3 枚举）。 */
    AttributeTypeEnum type();

    /** 是否为空值（空实例口径）。 */
    boolean isEmpty();

    /** 串形态（v3 现网口径；落库 / 出参唯一产地）。 */
    String toText();
}
