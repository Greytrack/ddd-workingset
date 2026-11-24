package com.airedgesoft.lib.restful.batch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 批量端点条数上限声明（缺省取全局配置 {@code airedge.restful.batch.max}，其缺省 200；超限 → 400
 * {@link com.airedgesoft.lib.restful.DomainRestCode#BATCH_LIMIT_EXCEEDED}）。
 *
 * <p>
 * 标注在 Controller 方法上后，{@link BatchLimitAspect} 扫描全部 {@link java.util.Collection} 型实参（ID 列表 / 批量载荷），任一超过上限即在方法
 * 执行前快速失败——防御「一次批量拉全表」的容量攻击与超长事务。巨量数据导出不适用本注解（应走流式 {@code streaming.StreamingExportSupport}）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BatchLimit {

    /** 条数上限（缺省 0 = 取全局配置 {@code airedge.restful.batch.max}；显式正值按端点收紧 / 放宽）。 */
    int max() default 0;
}
