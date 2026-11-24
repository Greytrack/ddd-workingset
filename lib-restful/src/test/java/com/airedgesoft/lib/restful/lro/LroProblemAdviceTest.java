package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LroProblemAdvice} 单测（无容器）：领域异常直译状态码 + 机器码；未映射异常兜底 500；协议层 4xx 按自带状态码透传。
 */
class LroProblemAdviceTest {

    private final LroProblemAdvice advice = new LroProblemAdvice();

    private final WebRequest request = mock(WebRequest.class);

    @DisplayName("领域异常翻译：OPERATION_NOT_DELETABLE 按错误类型直译 409 并携带机器码 code 扩展字段")
    @Test
    void onDomainException_translatesTypeToStatusAndCarriesCode() {
        ProblemDetail problem = advice
                .onDomainException(new DomainException(DomainRestCode.OPERATION_NOT_DELETABLE, "101"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", DomainRestCode.OPERATION_NOT_DELETABLE.getCode());
    }

    @DisplayName("兜底行为：未映射的运行时异常渲染 500")
    @Test
    void onException_unmappedException_fallsBackTo500() {
        ProblemDetail problem = advice.onException(new IllegalStateException("boom"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @DisplayName("协议层异常透传：Spring MVC 自带 4xx 按原状态码透传，不升级 500")
    @Test
    void onException_errorResponse4xx_passesThroughOwnStatus() {
        // Spring MVC 协议层异常（如缺参数）自带 4xx：按自带状态码透传，不升级 500
        org.springframework.web.bind.MissingServletRequestParameterException missing = new org.springframework.web.bind.MissingServletRequestParameterException(
                "pageToken", "String");
        ProblemDetail problem = advice.onException(missing, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @DisplayName("requestId 回填：缺失 X-Request-Id 请求头时扩展字段回退 n/a")
    @Test
    void requestId_missingHeaderFallsBackToNa() {
        when(request.getHeader("X-Request-Id")).thenReturn(null);
        ProblemDetail problem = advice.onDomainException(
                new DomainException(DomainRestCode.OPERATION_NOT_FOUND, "missing"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getProperties()).containsEntry("requestId", "n/a");
    }
}
