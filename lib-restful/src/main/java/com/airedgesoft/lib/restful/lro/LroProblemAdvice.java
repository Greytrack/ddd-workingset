package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.problem.ProblemDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * LRO 通用端点的错误契约兜底（RFC 9457 problem+json，规范 §8；§9 第 14 条同型）：lib 自托管 Controller （{@link OperationsController}）不属任何 BC，无
 * BC advice 覆盖——不兜底时异常将落入宿主 / legacy catch-all （Result 包装 + 500）。{@code basePackages} 精确到本包，不波及任何 BC 端点；映射与构建统一委托
 * {@link ProblemDetails}，优先级最高以先于 legacy catch-all 生效。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.airedgesoft.lib.restful.lro")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LroProblemAdvice {

    /** 业务异常：{@code DomainErrorType} → 状态码直译 + 稳定机器码（规范 §8）。 */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail onDomainException(DomainException ex, WebRequest request) {
        return ProblemDetails.build(ex, requestId(request));
    }

    /**
     * 兜底：Spring MVC 协议层异常（{@link ErrorResponse} 的 4xx）按自带状态码返回；其余未映射异常一律转 500 problem+json，阻断穿透到 legacy
     * GlobalExceptionHandler（规范 §1.4 / §5）。
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onException(Exception ex, WebRequest request) {
        String reqId = requestId(request);
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return ProblemDetails.build(errorResponse, reqId);
        }
        log.error("LRO 端点未处理异常, requestId={}", reqId, ex);
        return ProblemDetails.internalServerError(reqId);
    }

    private String requestId(WebRequest request) {
        return ProblemDetails.requestIdOrUnknown(request.getHeader(ProblemDetails.REQUEST_ID_HEADER));
    }
}
