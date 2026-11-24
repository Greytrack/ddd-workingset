package com.airedgesoft.lib.restful.path;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求 URI → 资源集合段派生的<b>唯一口径</b>（ETag 并发身份、字段开关集合键、ABAC 集合裁决共用）。
 *
 * <p>
 * <b>contextPath 必须剥离</b>：Servlet 规范下 {@link HttpServletRequest#getRequestURI()} 含 contextPath，部署
 * {@code server.servlet.context-path=/plm} 时真实 URI 为 {@code /plm/v1/demoTasks/101}；不做剥离则版本前缀判定恒失败， 所有依赖集合段的协议治理（§6.7
 * 并发身份、§20 字段开关、§19 ABAC）会整体静默失效且无任何日志。
 *
 * <p>
 * <b>派生规则</b>：剥 contextPath 后取 {@code /v1/} 之后的首个路径段——{@code /v1/demoTasks/101} → {@code demoTasks}； 集合级冒号动词
 * {@code /v1/demoTasks:batchUpdate} → {@code demoTasks}（冒号后动词剥离）；嵌套子资源取顶层集合段。 无法解析 → {@code null}（调用方按「无集合上下文」处理）。
 */
public final class RequestCollections {

    /** 资源相对名的路径前缀（与 {@code @RequestMapping("/v1/...")} 一致）。 */
    private static final String API_PREFIX = "/v1/";

    private RequestCollections() {
    }

    /** 路径段百分号解码（`+` 在路径中不是空格，需先转义再交给 URLDecoder）。 */
    private static String decode(String segment) {
        if (segment.indexOf('%') < 0 && segment.indexOf('+') < 0) {
            return segment;
        }
        try {
            return java.net.URLDecoder.decode(segment.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 非法百分号序列：按原样返回，交由上游 400（此处只做派生，不承担校验）
            return segment;
        }
    }

    /** 当前请求的集合段（无请求 / 非 {@code /v1/} 前缀 / 无法解析 → {@code null}）。 */
    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return of(request.getRequestURI(), request.getContextPath());
    }

    /** 按 URI 与 contextPath 派生集合段（contextPath 为空 / 不匹配时按原样解析，并从首个 {@code /v1/} 段起算）。 */
    public static String of(String requestUri, String contextPath) {
        if (requestUri == null) {
            return null;
        }
        String path = requestUri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith(API_PREFIX)) {
            // 兜底：contextPath 未声明但 URI 仍带前缀段（如网关重写、forward），从首个 /v1/ 起算
            int index = path.indexOf(API_PREFIX);
            if (index < 0) {
                return null;
            }
            path = path.substring(index);
        }
        String segment = path.substring(API_PREFIX.length());
        int slash = segment.indexOf('/');
        if (slash >= 0) {
            segment = segment.substring(0, slash);
        }
        // 先解码（路由侧按解码后的路径段匹配，派生侧必须同口径）：%3A → ':'，否则集合键漂移成 demoTasks%3AbatchUpdate
        segment = decode(segment);
        // 矩阵参数（`;v=1`）不是资源名的一部分
        int semicolon = segment.indexOf(';');
        if (semicolon >= 0) {
            segment = segment.substring(0, semicolon);
        }
        int colon = segment.indexOf(':');
        if (colon >= 0) {
            segment = segment.substring(0, colon);
        }
        return segment.isBlank() ? null : segment;
    }
}
