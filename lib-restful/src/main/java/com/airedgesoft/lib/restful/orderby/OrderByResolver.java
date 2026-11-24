package com.airedgesoft.lib.restful.orderby;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link Sortable} 排序解析器（Aspect）：方法执行前解析 {@code ?orderBy=field1 desc,field2 asc} 并按注解白名单校验，解析结果挂请求属性
 * {@link #REQUEST_ATTRIBUTE}（经 {@link #currentOrderBy()} 取用，不侵入 Query record 签名）。
 *
 * <p>
 * 未知字段 / 方向非法 → 400（方法体不执行）。主键兜底（末尾强制主键升序）由调用方按游标分页需要自行启用： {@code OrderByResolver.currentOrderBy(primaryKey)}。无 HTTP
 * 请求上下文时跳过切面逻辑（单测 / 作业线程直调用 {@link OrderBy#resolve(String, java.util.Set, String)} 手工链）。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 17)
public class OrderByResolver {

    /** 解析后的排序请求属性键。 */
    public static final String REQUEST_ATTRIBUTE = "airedge.restful.orderBy";

    @Around("@annotation(com.airedgesoft.lib.restful.orderby.Sortable)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Sortable annotation = ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(Sortable.class);
        HttpServletRequest request = currentRequest();
        if (annotation != null && request != null) {
            request.setAttribute(REQUEST_ATTRIBUTE,
                    OrderBy.resolve(request.getParameter(annotation.param()), java.util.Set.of(annotation.value())));
        }
        return pjp.proceed();
    }

    /** 当前请求已解析的排序（未标注 {@link Sortable} 或无请求上下文 → null）。 */
    public static OrderBy currentOrderBy() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : (OrderBy) request.getAttribute(REQUEST_ATTRIBUTE);
    }

    /** 当前请求已解析的排序 + 主键升序兜底（游标分页场景；primaryKey 空白时不兜底）。 */
    public static OrderBy currentOrderBy(String primaryKey) {
        OrderBy orderBy = currentOrderBy();
        if (primaryKey == null || primaryKey.isBlank()) {
            return orderBy;
        }
        if (orderBy == null) {
            return OrderBy.resolve(null, java.util.Set.of(), primaryKey);
        }
        return withPrimaryKey(orderBy, primaryKey);
    }

    private static OrderBy withPrimaryKey(OrderBy orderBy, String primaryKey) {
        if (orderBy.includes(primaryKey)) {
            return orderBy;
        }
        java.util.List<OrderBy.SortKey> keys = new java.util.ArrayList<>(orderBy.keys());
        keys.add(new OrderBy.SortKey(primaryKey, SortDirection.ASC));
        return new OrderBy(java.util.List.copyOf(keys));
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
