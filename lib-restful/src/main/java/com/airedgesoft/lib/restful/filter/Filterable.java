package com.airedgesoft.lib.restful.filter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * List 端点过滤白名单声明（AIP-160 子集；配合 {@link FilterAspect} / {@link FilterValidator} / {@link FilterParser}）。
 *
 * <p>
 * 标注在 Controller 方法上后，{@link FilterAspect} 在方法执行前解析 {@code ?filter=...}（参数名可配）并按 {@link #value()} 白名单校验： 语法非法 → 400
 * FILTER_SYNTAX_INVALID、未知字段 → 400 FILTER_FIELD_UNKNOWN（均含 fieldViolations）；解析结果挂请求属性
 * {@link FilterAspect#REQUEST_ATTRIBUTE}（经 {@link FilterAspect#currentAst()} 取用，供 Provider 下推）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Filterable {

    /** 请求参数名（缺省 {@code filter}）。 */
    String param() default "filter";

    /** 可过滤字段白名单。 */
    String[] value() default {};
}
