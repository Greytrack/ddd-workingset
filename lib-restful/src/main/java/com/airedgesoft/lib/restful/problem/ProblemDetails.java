package com.airedgesoft.lib.restful.problem;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.FieldViolation;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

/**
 * RFC 9457 Problem Details 的共享构建与状态码映射（v4 共享工具，lib-restful；审计的机制落点）。
 *
 * <p>
 * <b>分工</b>：各 BC 自持覆盖本 BC adapters 包的 {@code @RestControllerAdvice}（规范 §9 第 14 条 —— handler 方法与 {@code basePackages} 是
 * BC 的）；本类收口<b>映射与构建逻辑</b>：类型 → 状态码、type URI slug、title、ProblemDetail 组装， 消除 demo-singlemodule 与 v4-alm 两份逐字重复（审计
 * FF-16）。
 *
 * <p>
 * <b>状态码锚点</b>：{@link DomainException#getType()} 错误码直译 —— 错误码在构造时必填（编译器强制），无消息关键词兜底（审计终态）。
 */
public final class ProblemDetails {

    private static final String ERROR_BASE = "https://api.airedgesoft.com/errors/";

    /** 请求追踪头（客户端透传请求标识；problem 扩展成员 {@code requestId} 的取值来源）。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** requestId 兜底显示值（未携带 / 空白时写入 problem 扩展成员与日志）。 */
    public static final String UNKNOWN_REQUEST_ID = "n/a";

    /**
     * detail 文案解析器（i18n）：缺省 = ResCode 模板经 args String.format 格式化（{@link DomainException#getMessage()}）；宿主注入
     * {@link DomainMessageResolver} 后自动接管（v4-restful-api.md §8）。
     */
    private static volatile DomainMessageResolver messageResolver = DomainException::getMessage;

    private ProblemDetails() {
    }

    /** 注册提示词解析器（由 {@link ProblemDetailsAutoConfiguration} 在 context 启动时按可选 Bean 装配；包级私有防外部误改）。 */
    static void setMessageResolver(DomainMessageResolver resolver) {
        if (resolver != null) {
            messageResolver = resolver;
        }
    }

    /** 领域异常 → HTTP 状态码：错误码（{@link DomainErrorType}）直译，无关键词兜底。 */
    public static HttpStatus statusOf(DomainException ex) {
        return HttpStatus.resolve(ex.getType().httpStatus());
    }

    /** 异常的 type URI slug：按 {@link DomainErrorType} 直译。 */
    public static String typeSlug(DomainException ex) {
        return switch (ex.getType()) {
            case NOT_FOUND -> "not-found";
            case CONFLICT -> "status-conflict";
            case ABORTED -> "aborted";
            case ALREADY_EXISTS -> "already-exists";
            case FAILED_PRECONDITION -> "failed-precondition";
            case RESOURCE_EXHAUSTED -> "resource-exhausted";
            case OUT_OF_RANGE -> "out-of-range";
            case INTERNAL, DATA_LOSS -> "internal";
            case UNAVAILABLE -> "unavailable";
            case DEADLINE_EXCEEDED -> "deadline-exceeded";
            case UNIMPLEMENTED -> "unimplemented";
            case UNPROCESSABLE_ENTITY -> "unprocessable-entity";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            default -> "invalid-argument";
        };
    }

    /** 状态码 → type URI slug（兜底路径用）。 */
    public static String slug(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "not-found";
            case CONFLICT -> "status-conflict";
            case UNPROCESSABLE_ENTITY -> "unprocessable-entity";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            // 与 DomainErrorType.INTERNAL/DATA_LOSS 同 slug：同一个 500 不因抛出形态不同而产生两种 type（§1.3）
            case INTERNAL_SERVER_ERROR -> "internal";
            case BAD_REQUEST -> "invalid-argument";
            default -> status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-');
        };
    }

    /** 状态码 → title。 */
    public static String titleOf(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Not Found";
            case CONFLICT -> "Conflict";
            case UNPROCESSABLE_ENTITY -> "Unprocessable Entity";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case INTERNAL_SERVER_ERROR -> "Internal Server Error";
            case BAD_REQUEST -> "Invalid Argument";
            default -> status.getReasonPhrase();
        };
    }

    /** requestId 解析：未携带 / 空白 → {@link #UNKNOWN_REQUEST_ID}（构建与日志侧共用口径）。 */
    public static String requestIdOrUnknown(String requestId) {
        return requestId != null && !requestId.isBlank() ? requestId : UNKNOWN_REQUEST_ID;
    }

    /** 组装 problem+json：type 指向 airedgesoft 错误目录，附 requestId 扩展字段（规范 §8）。 */
    public static ProblemDetail build(HttpStatus status, String detail, String typeSlug, String title,
            String requestId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_BASE + typeSlug));
        problem.setTitle(title);
        problem.setProperty("requestId", requestIdOrUnknown(requestId));
        return problem;
    }

    /**
     * 领域异常一站式构建（状态码 + slug + title 直译；detail 经 {@link DomainMessageResolver} 解析 i18n，未配置/未命中回退 message）。 附扩展成员
     * {@code code} = 业务错误码（v4-restful-api.md §8 稳定机器码；码值冻结，见异常规范 §2.2）。
     */
    public static ProblemDetail build(DomainException ex, String requestId) {
        HttpStatus status = statusOf(ex);
        ProblemDetail problem = build(status, messageResolver.resolve(ex), typeSlug(ex), titleOf(status), requestId);
        problem.setProperty("code", ex.getCode());
        if (!ex.getFieldViolations().isEmpty()) {
            // 字段级违规明细（403 越权字段 / 400 字段约束违规等，规范 §8 / §19 / §20）：载荷只带 field + ResCode + args，
            // description 在本边界经 resolver 按 locale 渲染 ResCode 模板（文案收口在 ResCode，领域层不拼文案）
            List<Map<String, Object>> items = new ArrayList<>(ex.getFieldViolations().size());
            for (FieldViolation violation : ex.getFieldViolations()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", violation.field());
                item.put("code", violation.code().getCode());
                item.put("description", messageResolver.resolve(violation.code(), violation.args()));
                items.add(item);
            }
            problem.setProperty("fieldViolations", items);
        }
        return problem;
    }

    /**
     * Spring MVC 协议层异常（实现 {@link ErrorResponse}：缺参 / 缺请求头 / 方法参数校验等）按其自带状态码构建， 避免落入 {@code Exception} 兜底被放大为 500；5xx
     * 一律按内部错误处理（不外泄技术细节）。无业务码，不带 {@code code}。
     */
    public static ProblemDetail build(ErrorResponse ex, String requestId) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            return internalServerError(requestId);
        }
        String detail = ex.getBody().getDetail();
        return build(status, detail != null ? detail : titleOf(status), slug(status), titleOf(status), requestId);
    }

    /** 500 系统内部未处理异常兜底构建（规范 §1.4/§5；不泄露技术异常堆栈或内部信息）。 */
    public static ProblemDetail internalServerError(String requestId) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误，请联系管理员并提供请求追踪标识", slug(HttpStatus.INTERNAL_SERVER_ERROR),
                titleOf(HttpStatus.INTERNAL_SERVER_ERROR), requestId);
    }
}
