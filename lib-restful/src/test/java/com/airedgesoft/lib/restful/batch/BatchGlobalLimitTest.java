package com.airedgesoft.lib.restful.batch;

import com.airedgesoft.lib.exception.DomainException;
import java.util.List;
import java.util.stream.IntStream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code @BatchLimit} 全局默认上限契约：未显式声明 max 时取全局配置（缺省 200）；显式 max 按端点覆盖。
 */
class BatchGlobalLimitTest {

    @SuppressWarnings("unused")
    static class SampleController {

        @BatchLimit
        void defaultLimit(List<String> ids) {
        }

        @BatchLimit(max = 2)
        void explicitLimit(List<String> ids) {
        }
    }

    @Test
    void globalDefault_appliesWhenAnnotationOmitsMax() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect(3);
        java.lang.reflect.Method method = SampleController.class.getDeclaredMethod("defaultLimit", List.class);

        assertThatThrownBy(() -> aspect.around(joinPoint(method, List.of("a", "b", "c", "d"))))
                .isInstanceOf(DomainException.class);
        assertThatCode(() -> aspect.around(joinPoint(method, List.of("a", "b", "c")))).doesNotThrowAnyException();
    }

    @Test
    void explicitMax_overridesGlobalDefault() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect(100);
        java.lang.reflect.Method method = SampleController.class.getDeclaredMethod("explicitLimit", List.class);

        assertThatThrownBy(() -> aspect.around(joinPoint(method, List.of("a", "b", "c"))))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void noArgConstructor_defaultsTo200() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect();
        java.lang.reflect.Method method = SampleController.class.getDeclaredMethod("defaultLimit", List.class);
        List<String> atLimit = IntStream.rangeClosed(1, 200).mapToObj(String::valueOf).toList();

        assertThatCode(() -> aspect.around(joinPoint(method, atLimit))).doesNotThrowAnyException();
    }

    private static ProceedingJoinPoint joinPoint(java.lang.reflect.Method method, Object... args) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.proceed()).thenReturn(new Object());
        when(pjp.getArgs()).thenReturn(args);
        return pjp;
    }
}
