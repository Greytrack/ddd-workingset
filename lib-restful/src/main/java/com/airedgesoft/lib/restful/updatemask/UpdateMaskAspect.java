package com.airedgesoft.lib.restful.updatemask;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link ValidUpdateMask} 切面：方法执行前完成掩码解析（AIP-134 模式）+ 白名单校验，并把<b>归一后的具体路径</b> {@link UpdateMask} 挂到请求属性
 * {@link #REQUEST_ATTRIBUTE}（Controller 或后续组件经 {@link #currentMask()} / request.getAttribute 取用）。
 *
 * <p>
 * <b>模式归一</b>：省略 → 全部已填充可写字段、{@code *} → 全量替换、空串 → no-op，经 {@link UpdateMask#resolve} 结合 {@code @RequestBody} 实参与白名单
 * 解析为 EXPLICIT 具体路径——下游 {@code List<String>} 契约（command.updateMask）无需感知模式。output-only 字段不在白名单内即被忽略（AIP-161）。
 *
 * <p>
 * 校验失败（未知字段 / 空路径段）在方法体执行<b>前</b>以 400 快速失败（DomainException → ProblemDetails）。无 HTTP 请求上下文 （单测直调 /
 * 定时作业）时跳过切面逻辑直接放行——显式路径可改用 {@link UpdateMask#parse} + {@link UpdateMask#validate} 手工链。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 16)
public class UpdateMaskAspect {

    /** 解析后的掩码请求属性键。 */
    public static final String REQUEST_ATTRIBUTE = "airedge.restful.updateMask";

    @Around("@annotation(com.airedgesoft.lib.restful.updatemask.ValidUpdateMask)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        ValidUpdateMask annotation = signature.getMethod().getAnnotation(ValidUpdateMask.class);
        HttpServletRequest request = currentRequest();
        if (annotation != null && request != null) {
            if (annotation.whitelist().length == 0) {
                // 编程错误立即暴露：空白名单 + 省略掩码会退化为「不写任何字段」或（旧实现）「全写」，两者都不是可预期契约
                throw new IllegalStateException(
                        "@ValidUpdateMask 必须声明非空 whitelist：" + signature.getMethod().toGenericString());
            }
            UpdateMask mask = UpdateMask.parse(request.getParameter(annotation.param()));
            Set<String> whitelist = new LinkedHashSet<>(Arrays.asList(annotation.whitelist()));
            mask.validate(whitelist);
            request.setAttribute(REQUEST_ATTRIBUTE, mask.resolve(requestBodyArg(signature, pjp.getArgs()), whitelist));
        }
        return pjp.proceed();
    }

    /** 取 {@code @RequestBody} 标注的实参（模式归一的值源）；无请求体 → null。 */
    private static Object requestBodyArg(MethodSignature signature, Object[] args) {
        Parameter[] parameters = signature.getMethod().getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            if (parameters[i].isAnnotationPresent(RequestBody.class)) {
                return args[i];
            }
        }
        return null;
    }

    /**
     * 覆盖当前请求已解析的掩码（供下游切面按预定义字段开关收窄 {@code *} 全量替换的候选集，§20：停用字段不属于「可写字段」）。
     */
    public static void override(jakarta.servlet.http.HttpServletRequest request, UpdateMask mask) {
        if (request != null) {
            request.setAttribute(REQUEST_ATTRIBUTE, mask);
        }
    }

    /** 当前请求已解析的掩码（未标注 {@link ValidUpdateMask} 或无请求上下文 → null）。 */
    public static UpdateMask currentMask() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : (UpdateMask) request.getAttribute(REQUEST_ATTRIBUTE);
    }

    /**
     * 当前请求已解析的掩码路径（省略掩码 / 无请求上下文 → null；空串 no-op → 空清单）， 供 command 的 {@code List<String>} 契约直接取用，省去各 Controller 重复判空样板。
     */
    public static java.util.List<String> currentMaskPaths() {
        UpdateMask mask = currentMask();
        return mask == null ? null : mask.paths();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
