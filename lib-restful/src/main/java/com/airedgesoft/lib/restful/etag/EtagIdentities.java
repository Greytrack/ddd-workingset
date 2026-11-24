package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.restful.path.RequestCollections;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 并发身份解析（规范 §6.7）：把「端点声明」与「运行时资源」解析为唯一的并发代数归属者。
 *
 * <p>
 * <b>资源名即键</b>：身份统一表示为 AIP-122 相对资源名（{@code collection/id}，如 {@code demoTasks/623809242866016}）—— 命名空间随资源集合，跨 BC /
 * 跨集合的同号资源不串（不同 id 空间的资源不会共用同一期望版本槽）。
 *
 * <p>
 * <b>标识锚点 = {@code name}</b>：各资源的 id 字段名因资源而异，框架无法按字段名通用取 id；{@code name} 是跨资源类型唯一的规范资源标识（恒为 {@code collection/{id}}
 * 形态），故以其为定位锚点（规范 §6.7 规则 2）。
 *
 * <p>
 * <b>解析顺序</b>：
 * <ol>
 * <li>{@link EtagResource#value()} 非空 → 该<b>路径变量</b>的运行时值 + 请求路径的集合段（{@code collection/变量值}）；
 * 子资源写端点由此指向<b>父聚合</b>；</li>
 * <li>未声明但方法只有<b>唯一路径变量</b> → 同口径回退（与写侧 {@code PreconditionAspect} 绑定一致）；</li>
 * <li>都没有 → 载荷自身（AIP-122 {@code name}，如创建响应——id 仅落库后可知；集合条目缺省口径）。</li>
 * </ol>
 */
final class EtagIdentities {

    private EtagIdentities() {
    }

    /** 端点声明的路径变量名（未声明或无值 → null）。 */
    static String declaredVariable(Method method) {
        EtagResource annotation = method == null ? null : method.getAnnotation(EtagResource.class);
        if (annotation == null || annotation.value().isBlank()) {
            return null;
        }
        return annotation.value();
    }

    /**
     * 解析资源相对名：显式路径变量优先；其次<b>唯一路径变量</b>回退（与写侧绑定同一口径，子资源端点由此指向父聚合）； 都没有 → 载荷自身的 {@code name}（非资源载荷 → null）。
     */
    static String resolve(Method method, Object payload, HttpServletRequest request) {
        String variable = declaredVariable(method);
        if (variable == null) {
            variable = uniquePathVariable(method);
        }
        if (variable != null) {
            String value = pathVariable(variable, request);
            if (value == null) {
                return null;
            }
            String collection = collection(request);
            return collection == null ? value : collection + "/" + value;
        }
        return ResourceName.of(payload);
    }

    /**
     * 写侧绑定目标：显式声明 → 唯一路径变量（运行时值优先，回退方法实参）→ null（无法确定身份）。 多个路径变量语义不唯一，必须显式 {@link EtagResource} 声明。
     */
    static String bindingTarget(Method method, Object[] args, HttpServletRequest request) {
        String variable = declaredVariable(method);
        int argumentIndex = -1;
        if (variable == null) {
            int found = -1;
            java.lang.annotation.Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (java.lang.annotation.Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof org.springframework.web.bind.annotation.PathVariable pathVariable) {
                        if (found >= 0) {
                            return null;
                        }
                        found = i;
                        variable = pathVariable.name().isBlank() ? pathVariable.value() : pathVariable.name();
                    }
                }
            }
            argumentIndex = found;
        }
        if (variable == null) {
            return null;
        }
        String id = pathVariable(variable, request);
        if (id == null && argumentIndex >= 0 && args != null && argumentIndex < args.length
                && args[argumentIndex] != null) {
            id = String.valueOf(args[argumentIndex]);
        }
        if (id == null) {
            return null;
        }
        String collection = collection(request);
        return collection == null ? id : collection + "/" + id;
    }

    /** 方法上唯一的路径变量名（0 个或多个 → null）。 */
    static String uniquePathVariable(Method method) {
        if (method == null) {
            return null;
        }
        String found = null;
        for (java.lang.annotation.Annotation[] annotations : method.getParameterAnnotations()) {
            for (java.lang.annotation.Annotation annotation : annotations) {
                if (annotation instanceof org.springframework.web.bind.annotation.PathVariable pathVariable) {
                    if (found != null) {
                        return null;
                    }
                    found = pathVariable.name().isBlank() ? pathVariable.value() : pathVariable.name();
                }
            }
        }
        return found;
    }

    /** 载荷自身的资源相对名（AIP-122 {@code name}；非资源载荷 → null）。 */
    static String selfId(Object payload) {
        return ResourceName.of(payload);
    }

    /** 路径变量运行时值（未绑定 → null）。 */
    static String pathVariable(String name, HttpServletRequest request) {
        Map<String, String> variables = pathVariables(request);
        return variables == null ? null : variables.get(name);
    }

    /**
     * 请求路径的集合段（{@code /v1/demoTasks/101} → {@code demoTasks}；集合级冒号动词 {@code /v1/demoTasks:batchUpdate} →
     * {@code demoTasks}）。contextPath（如部署 {@code /plm}）由 {@link RequestCollections} 统一剥离；非 {@code /v1/} 前缀或无法解析 → null。
     */
    static String collection(HttpServletRequest request) {
        return RequestCollections.of(request);
    }

    /** 请求绑定的 URI 模板变量（非 web 上下文 → null）。 */
    @SuppressWarnings("unchecked")
    static Map<String, String> pathVariables(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : null;
    }

    /** 当前请求（非 web 线程 → null）。 */
    static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
