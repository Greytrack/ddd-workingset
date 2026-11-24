package com.airedgesoft.lib.restful.orderby;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * List 端点排序白名单声明（AIP-132；配合 {@code OrderByResolver} / {@link OrderBy#resolve} 使用）。
 *
 * <p>
 * 标注在 Controller 方法上后，{@code OrderByAspect} 在方法执行前解析请求参数（默认 {@code orderBy}，可配）并按 {@link #value()} 白名单校验， 结果挂请求属性
 * {@code airedge.restful.orderBy}；未知字段 → 400（{@code DomainRestCode.ORDER_BY_FIELD_UNKNOWN} + fieldViolations）。
 * 白名单值须与契约字段同形（camelCase 点号路径）；状态机字段可排序但须在文档声明（排序不改变资源）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sortable {

    /** 请求参数名（缺省 {@code orderBy}）。 */
    String param() default "orderBy";

    /** 可排序字段白名单。 */
    String[] value() default {};
}
