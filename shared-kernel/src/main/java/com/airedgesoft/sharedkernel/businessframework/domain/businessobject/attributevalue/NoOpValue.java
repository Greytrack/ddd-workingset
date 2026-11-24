package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;

/**
 * 值对象：空实现（图标 / 虚拟 / 聚合三种合并，靠 {@link #type()} 区分）。
 *
 * <p>
 * 无独立值语义：{@link #isEmpty()} 恒真、{@link #toText()} 恒空，值校验 NoOp 放行。
 */
public record NoOpValue(AttributeTypeEnum type) implements AttributeValue {

    public NoOpValue {
        type = type == null ? AttributeTypeEnum.VIRTUAL : type;
    }

    /** 串形态构造（忽略入参串，仅按类型产出）。 */
    public static NoOpValue fromText(AttributeTypeEnum type, String text) {
        return new NoOpValue(type);
    }

    /** 空实例。 */
    public static NoOpValue empty(AttributeTypeEnum type) {
        return new NoOpValue(type);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String toText() {
        return "";
    }
}
