package com.airedgesoft.lib.restful.dryrun;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 预检（validateOnly）语义声明（v4-restful-api.md §3 create 行 / Tier A「validateOnly 预检回滚」）。
 *
 * <p>
 * 请求含 {@code ?validateOnly=true}（参数名可配 {@link #param()}）时：{@link DryRunAspect} 用 {@code TransactionTemplate}
 * （{@code PROPAGATION_REQUIRED}）包住目标方法并<b>完整执行</b>（全部校验、领域规则、事件发布、响应读回装配照常跑），成功后置 {@code setRollbackOnly}
 * 并正常返回执行结果（HTTP 200）——数据落库被回滚，调用方得到「校验通过」的真实结论而非模拟推演。
 *
 * <p>
 * <b>事务边界</b>：切面自持，不要求宿主写端点声明 {@code @Transactional}；应用层 {@code *CommandService} 的 {@code @Transactional} 以
 * {@code REQUIRED} 加入本事务。无 {@link org.springframework.transaction.PlatformTransactionManager}
 * 时降级为直接执行并告警（校验结论仍成立，但无法回滚）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DryRun {

    /** 预检开关请求参数名（缺省 {@code validateOnly}；值 {@code true}/1 视为开启）。 */
    String param() default "validateOnly";
}
