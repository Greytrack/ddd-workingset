package com.airedgesoft.lib.restful.updatemask;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PATCH updateMask 的声明式校验标记（v4-restful-api.md §3 / AIP-161；Tier B「声明式注解驱动」）。
 *
 * <p>
 * 标注在 Controller 方法上后，{@link UpdateMaskAspect} 在方法执行前完成：① 从请求参数（默认 {@code updateMask}，可配）读取原始掩码 → ②
 * {@link UpdateMask#parse}（省略=全部已填充、{@code *}=全量替换、空串=no-op；空路径段 400）→ ③ 按 {@link #whitelist()} 校验（未知字段 400 +
 * {@code fieldViolations}）→ ④ 解析结果挂请求属性 {@link UpdateMaskAspect#REQUEST_ATTRIBUTE}（Controller 经
 * {@code request.getAttribute} 或 {@link UpdateMaskAspect#currentMask()} 取用，不侵入 Command record 签名）。
 *
 * <p>
 * <b>与 ABAC 的边界</b>：本注解只做「语法 + 静态白名单」400 校验（§19.3 校验链第 1 步）；字段级写权限 / 值约束的 403 校验由
 * {@code com.airedgesoft.lib.restful.abac.AbacWriteGuard} 承担。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUpdateMask {

    /** 请求参数名（缺省 {@code updateMask}）。 */
    String param() default "updateMask";

    /** 可更新字段白名单（camelCase 路径；状态机字段严禁列入，规范 §4.4.3）。 */
    String[] whitelist() default {};
}
