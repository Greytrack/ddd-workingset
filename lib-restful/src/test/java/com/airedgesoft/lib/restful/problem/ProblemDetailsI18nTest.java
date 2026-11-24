package com.airedgesoft.lib.restful.problem;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.DomainResCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提示词国际化解析缝隙单测（v4-restful-api.md §8）：缺省 = ResCode 模板格式化（{@link DomainException#getMessage()}）； 注入
 * {@link DomainMessageResolver} 后接管 detail 文案， 状态码 / type 仍由 {@link DomainErrorType} 直译（不受文案影响）。
 */
class ProblemDetailsI18nTest {

    /** 测试用模块错误码：业务码 + 文案模板 + HTTP 语义三合一（DomainResCode 单参数抛法）。 */
    private enum TestResultCode implements DomainResCode {
        TASK_NOT_EXIST(DomainErrorType.NOT_FOUND, 20030001, "任务不存在: %s");

        private final DomainErrorType type;
        private final int code;
        private final String desc;

        TestResultCode(DomainErrorType type, int code, String desc) {
            this.type = type;
            this.code = code;
            this.desc = desc;
        }

        @Override
        public DomainErrorType getType() {
            return type;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Override
        public String getDesc() {
            return desc;
        }
    }

    @AfterEach
    void reset() {
        ProblemDetails.setMessageResolver(DomainException::getMessage);
    }

    @Test
    void domainResCode_singleArg_argsFormattedByDefault() {
        ProblemDetail problem = ProblemDetails.build(new DomainException(TestResultCode.TASK_NOT_EXIST, 1L), "req-1");
        assertThat(problem.getDetail()).isEqualTo("任务不存在: 1");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType().toString()).endsWith("/not-found");
        assertThat(problem.getProperties().get("code")).isEqualTo(20030001);
    }

    @Test
    void dualArg_typeAndCodeSeparated() {
        DomainException ex = new DomainException(DomainErrorType.CONFLICT, TestResultCode.TASK_NOT_EXIST, 2L);
        ProblemDetail problem = ProblemDetails.build(ex, "req-2");
        assertThat(problem.getDetail()).isEqualTo("任务不存在: 2");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType().toString()).endsWith("/status-conflict");
        assertThat(problem.getProperties().get("code")).isEqualTo(20030001);
    }

    @Test
    void injectedResolver_keysOnBusinessCode() {
        ProblemDetails.setMessageResolver(ex -> "[" + ex.getCode() + "] " + ex.getMessage());
        ProblemDetail problem = ProblemDetails.build(new DomainException(TestResultCode.TASK_NOT_EXIST, 9L), "req-3");
        assertThat(problem.getDetail()).isEqualTo("[20030001] 任务不存在: 9");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void statusMapping_unauthorizedAndForbidden() {
        assertThat(ProblemDetails.slug(HttpStatus.UNAUTHORIZED)).isEqualTo("unauthorized");
        assertThat(ProblemDetails.titleOf(HttpStatus.UNAUTHORIZED)).isEqualTo("Unauthorized");

        assertThat(ProblemDetails.slug(HttpStatus.FORBIDDEN)).isEqualTo("forbidden");
        assertThat(ProblemDetails.titleOf(HttpStatus.FORBIDDEN)).isEqualTo("Forbidden");
    }

    @Test
    void internalServerError_buildStandardProblemDetails() {
        ProblemDetail problem = ProblemDetails.internalServerError("req-500");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getType().toString()).endsWith("/internal");
        assertThat(problem.getDetail()).isEqualTo("系统内部错误，请联系管理员并提供请求追踪标识");
        assertThat(problem.getProperties().get("requestId")).isEqualTo("req-500");
    }

    @Test
    void errorResponse_clientError_keepsOwnStatus_withoutCode() {
        ProblemDetail problem = ProblemDetails.build(new MissingServletRequestParameterException("pageSize", "Integer"),
                "req-400");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).endsWith("/invalid-argument");
        assertThat(problem.getDetail()).contains("pageSize");
        assertThat(problem.getProperties()).doesNotContainKey("code").containsEntry("requestId", "req-400");
    }

    @Test
    void errorResponse_unmappedClientStatus_derivesSlugFromReasonPhrase() {
        ProblemDetail problem = ProblemDetails.build(new ErrorResponseException(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                "req-415");
        assertThat(problem.getStatus()).isEqualTo(415);
        assertThat(problem.getType().toString()).endsWith("/unsupported-media-type");
        assertThat(problem.getTitle()).isEqualTo("Unsupported Media Type");
    }

    @Test
    void errorResponse_serverError_fallsBackToInternalServerError() {
        ProblemDetail problem = ProblemDetails.build(new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE),
                "req-503");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getType().toString()).endsWith("/internal");
    }

    @Test
    void internalServerError_sameTypeOnBothConstructionPaths() {
        // 同一个 500 不允许因抛出形态不同产生两种 type（前端按 type 分支会漏掉兜底路径，§1.3）
        String fromException = ProblemDetails.typeSlug(new com.airedgesoft.lib.exception.DomainException(
                com.airedgesoft.lib.exception.DomainErrorType.INTERNAL,
                com.airedgesoft.lib.exception.CommonResCode.VERSION_CONFLICT));
        String fromFallback = ProblemDetails.slug(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(fromFallback).isEqualTo(fromException).isEqualTo("internal");
    }
}
