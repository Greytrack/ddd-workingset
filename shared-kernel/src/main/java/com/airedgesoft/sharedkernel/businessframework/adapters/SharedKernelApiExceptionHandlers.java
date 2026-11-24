package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.problem.ProblemDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Shared Kernel adapters（跨 BC 资源入站适配：workingsets / productSpaces / trackers / pageViews / buttons / comments /
 * workflowInstances / operations 八族）的统一错误契约（RFC 9457 problem+json）。
 *
 * <p>
 * 规范 §9 第 14 条口径：覆盖本包的 {@code @RestControllerAdvice}，错误不经其他 BC 处理器顺带覆盖、不穿透 legacy catch-all； 映射与构建逻辑在
 * {@link ProblemDetails}（lib-restful），本类只保留 {@code basePackages} 与 handler 绑定。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.airedgesoft.sharedkernel.businessframework.adapters")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SharedKernelApiExceptionHandlers {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        return ProblemDetails.build(HttpStatus.BAD_REQUEST, "参数[" + ex.getName() + "]类型不合法: " + ex.getValue(),
                "invalid-argument", "Invalid Argument", requestId(request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        return ProblemDetails.build(HttpStatus.BAD_REQUEST, "请求体不可读或 JSON 格式非法", "invalid-argument", "Invalid Argument",
                requestId(request));
    }

    /** Bean Validation 失败：一次性返回全部违规字段（规范 §8）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex, WebRequest request) {
        return ProblemDetails.build(HttpStatus.BAD_REQUEST, "请求参数校验失败", "invalid-argument", "Invalid Argument",
                requestId(request));
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail onDomainException(DomainException ex, WebRequest request) {
        // 业务拒绝是预期 4xx，不打堆栈；warn 让错误码频率在生产可统计。
        log.warn("共享资源业务拒绝：requestId={} code={} detail={}", requestId(request), ex.getResCode().getCode(),
                ex.getMessage());
        return ProblemDetails.build(ex, requestId(request));
    }

    /**
     * 兜底：Spring MVC 协议层异常按自带 4xx 状态码返回；其余未映射异常一律转 500 problem+json，阻断穿透到 legacy GlobalExceptionHandler。
     * 配置欠账类内部故障（如页面视图映射配置缺失）同样落此兜底：明细经 {@code log.error} 堆栈入日志（requestId 可查），detail 保持通用文案。
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onException(Exception ex, WebRequest request) {
        String reqId = requestId(request);
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return ProblemDetails.build(errorResponse, reqId);
        }
        log.error("系统内部未处理异常, requestId={}", reqId, ex);
        return ProblemDetails.internalServerError(reqId);
    }

    private String requestId(WebRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header != null ? header : "n/a";
    }
}
