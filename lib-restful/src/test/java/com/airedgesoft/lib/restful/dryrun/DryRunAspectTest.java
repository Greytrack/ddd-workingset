package com.airedgesoft.lib.restful.dryrun;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code @DryRun} 切面单测（组件 6；validateOnly 开关判定与无事务放行语义——事务内 setRollbackOnly 路径由宿主 {@code TransactionInterceptor}
 * 集成验证，此处覆盖无事务契约）。
 */
class DryRunAspectTest {

    private final DryRunAspect aspect = new DryRunAspect();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void validateOnlyTrue_noTransaction_proceedsAndPassesResultThrough() throws Throwable {
        request("validateOnly", "true");
        Object result = aspect.around(joinPoint());
        assertThat(result).isEqualTo("执行结果"); // 校验逻辑真实执行、结果正常返回（无事务 → 放行告警路径）
    }

    @Test
    void validateOnlyAbsent_plainExecution() throws Throwable {
        request("validateOnly", null);
        Object result = aspect.around(joinPoint());
        assertThat(result).isEqualTo("执行结果");
    }

    @Test
    void validateOnlyOne_isTruthy() throws Throwable {
        request("validateOnly", "1");
        assertThatCode(() -> aspect.around(joinPoint())).doesNotThrowAnyException();
    }

    @Test
    void validateOnlyFalse_isFalsy() throws Throwable {
        request("validateOnly", "false");
        assertThat(aspect.around(joinPoint())).isEqualTo("执行结果");
    }

    @Test
    void proceedException_propagates() throws Throwable {
        request("validateOnly", "true");
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(DryRunAspectTest.class.getDeclaredMethod("sample"));
        when(pjp.proceed()).thenThrow(new IllegalStateException("校验失败"));
        assertThatCode(() -> aspect.around(pjp)).isInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unused")
    private static void sample() {
    }

    private static void request(String param, String value) {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getParameter(param)).thenReturn(value);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest, httpResponse));
    }

    private ProceedingJoinPoint joinPoint() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(DryRunAspectTest.class.getDeclaredMethod("annotatedSample"));
        when(pjp.proceed()).thenReturn("执行结果");
        return pjp;
    }

    @SuppressWarnings("unused")
    @DryRun
    private static void annotatedSample() {
    }
}
