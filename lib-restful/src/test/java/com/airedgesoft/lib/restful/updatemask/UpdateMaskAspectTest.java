package com.airedgesoft.lib.restful.updatemask;

import com.airedgesoft.lib.exception.DomainException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code @ValidUpdateMask} 切面单测（组件 3 声明式入口；请求属性挂载与 400 快速失败）。
 */
class UpdateMaskAspectTest {

    private final UpdateMaskAspect aspect = new UpdateMaskAspect();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void annotatedMethod_parsesValidatesAndAttachesAttribute() throws Throwable {
        request("updateMask", "title,description");
        aspect.around(joinPoint(method("patch")));
        UpdateMask mask = UpdateMaskAspect.currentMask();
        assertThat(mask).isNotNull();
        assertThat(mask.paths()).containsExactly("title", "description");
    }

    @Test
    void annotatedMethod_unknownField_failsFast400() throws Throwable {
        request("updateMask", "title,typo");
        assertThatThrownBy(() -> aspect.around(joinPoint(method("patch")))).isInstanceOf(DomainException.class);
        assertThat(UpdateMaskAspect.currentMask()).isNull();
    }

    @Test
    void annotatedMethod_omittedMask_resolvesAllPopulatedWithoutError() throws Throwable {
        request("updateMask", null);
        assertThatCode(() -> aspect.around(joinPoint(method("patch")))).doesNotThrowAnyException();
        // 省略 → ALL_POPULATED（AIP-134:104），无 @RequestBody 值源 → 归一为空路径，不再 400
        assertThat(UpdateMaskAspect.currentMask()).isNotNull();
    }

    @Test
    void customParamName_resolved() throws Throwable {
        request("mask", "title");
        assertThatCode(() -> aspect.around(joinPoint(method("patchCustomParam")))).doesNotThrowAnyException();
        assertThat(UpdateMaskAspect.currentMask().paths()).containsExactly("title");
    }

    @Test
    void noRequestContext_passesThrough() throws Throwable {
        assertThatCode(() -> aspect.around(joinPoint(method("patch")))).doesNotThrowAnyException();
    }

    @SuppressWarnings("unused")
    static class SampleController {

        @ValidUpdateMask(whitelist = {"title", "description"})
        void patch() {
        }

        @ValidUpdateMask(param = "mask", whitelist = {"title"})
        void patchCustomParam() {
        }
    }

    private static java.lang.reflect.Method method(String name) throws NoSuchMethodException {
        return SampleController.class.getDeclaredMethod(name);
    }

    private static void request(String param, String value) {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getParameter(param)).thenReturn(value);
        // 真实属性存储（切面把解析结果挂请求属性，currentMask() 经 getAttribute 读取）
        java.util.Map<String, Object> attributes = new java.util.HashMap<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(httpRequest).setAttribute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        when(httpRequest.getAttribute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest, httpResponse));
    }

    private ProceedingJoinPoint joinPoint(java.lang.reflect.Method sampleMethod) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod);
        when(pjp.proceed()).thenReturn(new Object());
        return pjp;
    }
}
