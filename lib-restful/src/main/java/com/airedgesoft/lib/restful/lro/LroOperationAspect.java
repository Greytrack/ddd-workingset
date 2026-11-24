package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.idempotency.IdempotencyFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link LroOperation} 触发装配切面：方法返回 {@link Operation} 时装配 202 Accepted + Location（全局
 * {@code /v1/operations/{operationId}}）+ Retry-After；写回 {@link HttpServletResponse}（与 {@code EtagResponseAdvice}
 * 同型实现，不改方法返回对象——避免 CGLIB 代理返回类型 checkcast 陷阱）。宿主注册 {@link OperationOwnerResolver} 时为 owner 缺失的 Operation
 * 补填发起人（「我的长运行任务列表」过滤源）并<b>回写 {@link OperationStore}</b>——触发端点在方法体内保存的记录尚无 owner，仅改写 wire 返回值会让
 * {@code GET /v1/operations} 的 owner 过滤口径对当前用户恒为空。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class LroOperationAspect {

    private final LroProperties properties;

    private final OperationOwnerResolver ownerResolver;

    private final OperationStore operationStore;

    public LroOperationAspect(LroProperties properties, OperationOwnerResolver ownerResolver,
            OperationStore operationStore) {
        this.properties = properties;
        this.ownerResolver = ownerResolver;
        this.operationStore = operationStore;
    }

    @Around("@annotation(com.airedgesoft.lib.restful.lro.LroOperation)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        requireIdempotencyKey();
        Object body = pjp.proceed();
        if (body instanceof Operation operation) {
            if (operation.owner() == null && ownerResolver != null) {
                String owner = ownerResolver.currentOwner();
                if (owner != null && !owner.isBlank()) {
                    body = fillOwner(operation, owner);
                }
            }
            HttpServletResponse response = currentResponse();
            if (response != null) {
                response.setStatus(202);
                response.setHeader("Location",
                        OperationsController.OPERATIONS_BASE_URI + "/" + operation.operationId());
                response.setIntHeader("Retry-After", retryAfterOf(pjp));
            }
        }
        return body;
    }

    /**
     * 补填发起人并回写 Store：先原子 create-if-absent（触发端点尚未落库的兜底——{@code saveIfAbsent} 不与并发 Worker 竞争覆写）； 已存在（含 Worker 抢先落库）则 CAS
     * 合并 owner 到「当前」状态，绝不以触发快照整对象覆写 Worker 已推进的进度/终态（OperationStore 契约）。 并发推进致 CAS 失败时回读最新值，owner 尽力而为（不阻断 202 装配）。
     */
    private Operation fillOwner(Operation operation, String owner) {
        String operationId = operation.operationId();
        Operation filled = operation.withOwner(owner);
        if (operationStore.saveIfAbsent(filled)) {
            return filled;
        }
        Operation latest = operationStore.find(operationId).orElse(filled);
        return operationStore.compareAndStatus(operationId, OperationStatus.of(latest), op -> op.withOwner(owner))
                .orElseGet(() -> operationStore.find(operationId).orElse(latest));
    }

    /**
     * 长流程（202 + LRO）端点强制幂等键（v4-restful-api §16：长流程「必须要求 Idempotency-Key」；§18 M5 机器调用方必须携带）。
     *
     * <p>
     * 触发端点缺失该头时同步返回 400（而非先跑一遍再让客户端无法安全重试）：同一次导入重试若各自生成 operationId，会重复执行并重复产出。
     */
    private void requireIdempotencyKey() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        String key = request.getHeader(IdempotencyFilter.KEY_HEADER);
        if (key == null || key.isBlank()) {
            throw new DomainException(DomainRestCode.LRO_IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private int retryAfterOf(ProceedingJoinPoint pjp) {
        LroOperation annotation = ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(LroOperation.class);
        return annotation != null && annotation.retryAfterSeconds() > 0
                ? annotation.retryAfterSeconds()
                : properties.getRetryAfterSeconds();
    }

    private static HttpServletResponse currentResponse() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getResponse();
        }
        return null;
    }
}
