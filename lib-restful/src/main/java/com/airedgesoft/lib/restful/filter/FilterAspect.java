package com.airedgesoft.lib.restful.filter;

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
 * {@link Filterable} 切面：方法执行前完成 filter 解析（{@link FilterParser}）+ 白名单校验（{@link FilterValidator}）， AST 挂请求属性
 * {@link #REQUEST_ATTRIBUTE}（经 {@link #currentAst()} 取用）。校验失败 400 快速失败（方法体不执行）。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 16)
public class FilterAspect {

    /** 解析后的 AST 请求属性键。 */
    public static final String REQUEST_ATTRIBUTE = "airedge.restful.filterAst";

    @Around("@annotation(com.airedgesoft.lib.restful.filter.Filterable)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Filterable annotation = ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(Filterable.class);
        HttpServletRequest request = currentRequest();
        if (annotation != null && request != null) {
            FilterAst ast = FilterValidator.parseAndValidate(request.getParameter(annotation.param()),
                    java.util.Set.of(annotation.value()));
            request.setAttribute(REQUEST_ATTRIBUTE, ast);
        }
        return pjp.proceed();
    }

    /** 当前请求已解析的过滤 AST（未标注 / 无请求 / 空过滤 → null）。 */
    public static FilterAst currentAst() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : (FilterAst) request.getAttribute(REQUEST_ATTRIBUTE);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
