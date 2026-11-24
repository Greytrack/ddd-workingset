package com.airedgesoft.lib.restful.sunset;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点弃用声明（RFC 8594 {@code Sunset} 头 + IETF Deprecation 头；Tier C「版本向前兼容性」运行时提示形态）。
 *
 * <p>
 * 标注在 Controller 方法（或类）上后，{@link SunsetInterceptor} 对命中请求自动下发：
 * <ul>
 * <li>{@code Deprecation: true} —— 端点已弃用（仍可用）；</li>
 * <li>{@code Sunset: <HTTP-date>} —— 计划停用时刻（{@link #dateTime()}，ISO-8601 instant，如 {@code 2027-01-01T00:00:00Z}； RFC
 * 8594 要求 IMF-fixdate 形态，由拦截器格式化）。</li>
 * </ul>
 * 迁移指引另经 OpenAPI description / {@code Link rel="sunset"} 后续扩展，本组件只做最小头下发。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sunset {

    /** 计划停用时刻（ISO-8601 instant 字符串，注解常量限制；解析失败快速失败）。 */
    String dateTime();
}
