package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

/**
 * 属性值校验支撑（包级）：纯类型化判定族共用的必填 / 文本 / 富文本 / 单选列表 / 数值 / 日期 / 图片判定。
 *
 * <p>
 * 判定分支、错误码与报错文案照搬现网（原 {@code *ValueSpecification} 与 {@code CascadeValueRangeChecker}），取数口径 v3 直调不变； 只把入参由裸
 * {@code Object} 换为已解析的类型化字段，不再 {@code toString()} 再解析。
 */
public interface IAttributeValidationSupport {

}
