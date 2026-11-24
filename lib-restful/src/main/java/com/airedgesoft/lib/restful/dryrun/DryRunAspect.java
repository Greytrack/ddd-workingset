package com.airedgesoft.lib.restful.dryrun;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link DryRun} 切面（Tier A「validateOnly 预检回滚」；v4-restful-api.md §3）。
 *
 * <p>
 * 执行序：① 请求参数含开关（{@code validateOnly=true/1}，参数名可配）？否 → 原样执行；② 是 → 用 {@link TransactionTemplate}
 * （{@code PROPAGATION_REQUIRED}）包住目标方法（完整校验 + 业务逻辑 + 领域事件 + 响应读回装配），已有事务则加入；③ 成功返回前置
 * {@code setRollbackOnly}——响应正常返回（调用方拿到真实执行结果），落库变更随事务回滚丢弃；④ 方法执行抛异常 → 事务本就回滚，无额外动作。
 *
 * <p>
 * <b>事务边界</b>：切面自持，不要求宿主写端点声明 {@code @Transactional}；应用层 {@code *CommandService} 的 {@code @Transactional} 以
 * {@code REQUIRED} 加入本事务。无 {@link PlatformTransactionManager} 时降级为直接执行并告警（校验结论仍成立，但无法回滚）。
 *
 * <p>
 * <b>顺序</b>：{@code HIGHEST_PRECEDENCE + 19}，位于协议切面族（条件请求处理 +15 / 掩码 +16 / 排序 +17 / 批量上限 +18）之后——预检回滚的是事务，参数校验类切面先行。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
public class DryRunAspect {

    private static final Logger log = LoggerFactory.getLogger(DryRunAspect.class);

    private final PlatformTransactionManager transactionManager;

    /** 无事务管理器：保留无事务降级路径（单测 / 非事务宿主）。 */
    public DryRunAspect() {
        this(null);
    }

    public DryRunAspect(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Around("@annotation(com.airedgesoft.lib.restful.dryrun.DryRun)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        DryRun annotation = ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(DryRun.class);
        if (annotation == null || !isValidateOnly(annotation.param())) {
            return pjp.proceed();
        }
        if (transactionManager == null) {
            log.warn("validateOnly 预检未配置 PlatformTransactionManager，回滚未生效: {}",
                    ((MethodSignature) pjp.getSignature()).getMethod());
            return pjp.proceed();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template.execute(status -> {
            try {
                Object result = pjp.proceed();
                status.setRollbackOnly();
                return result;
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                // 受检异常不属于控制器方法契约；包装为编程错误，避免新增自定义异常类型（ExceptionArchitectureRulesTest）
                throw new IllegalStateException("DryRun 目标方法抛出受检异常", t);
            }
        });
    }

    private static boolean isValidateOnly(String param) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String raw = request.getParameter(param);
            return raw != null && ("true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()));
        }
        return false;
    }
}
