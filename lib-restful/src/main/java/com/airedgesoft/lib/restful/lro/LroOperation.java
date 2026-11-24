package com.airedgesoft.lib.restful.lro;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * LRO 异步操作触发声明（AIP-151；v4-restful-api.md §4 自定义方法异步形态，Tier B「声明式接管」；Location 统一使用全局
 * {@code /v1/operations/{operationId}} 形态，非资源内嵌 {@code /v1/{res}/{id}/operations/...}）。
 *
 * <p>
 * 标注在触发端点（通常 {@code POST /v1/{res}:verb}）上后，{@link LroOperationAspect} 拦截：方法返回 {@link Operation} 时把响应装配为 <b>202
 * Accepted</b> + {@code Location: /v1/operations/{operationId}} + {@code Retry-After}（秒，注解可覆盖配置缺省）—— 返回体保持 Operation
 * JSON（调用方可立即拿到初始进度）。方法返回非 Operation（同步路径 / 降级实现）→ 原样透传不装配。
 *
 * <p>
 * <b>Worker 协作</b>：方法体负责 {@code store.save(Operation.pending(Operation.newId(), taskType()))} 并提交异步任务 （Worker 推进
 * {@code withProgress} / 终态 {@code withResult} / {@code withError}）；触发端点本身不做耗时工作。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LroOperation {

    /** 任务类型（审计 / 路由 / 监控维度，如 {@code demoTaskBatchCopy}）。 */
    String taskType();

    /** Retry-After 秒数（缺省 0 = 用 {@code airedge.restful.lro.retry-after-seconds} 配置）。 */
    int retryAfterSeconds() default 0;
}
