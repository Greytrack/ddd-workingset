package com.airedgesoft.lib.restful.batch;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BatchResponses.align 稀疏对齐与 @BatchLimit 上限单测（组件 5）。
 */
class BatchTest {

    // ── BatchAlignment ──

    @Test
    void align_preservesRequestOrderAndReportsMissing() {
        record Task(String id) {
        }
        List<Task> found = List.of(new Task("3"), new Task("1")); // 存储乱序返回
        BatchAlignment<Task> aligned = BatchAlignment.align(List.of("1", "2", "3"), found, Task::id);
        assertThat(aligned.found()).extracting(Task::id).containsExactly("1", "3"); // 按请求顺序
        assertThat(aligned.missing()).containsExactly("2"); // 缺失静默（稀疏返回）
    }

    @Test
    void align_deduplicatesRequestedIds() {
        record Task(String id) {
        }
        BatchAlignment<Task> aligned = BatchAlignment.align(List.of("1", "1", "2"), List.of(new Task("1")), Task::id);
        assertThat(aligned.found()).hasSize(1);
        assertThat(aligned.missing()).containsExactly("2");
    }

    @Test
    void align_emptyOrMisalignedInputs_degenerateSafely() {
        BatchAlignment<String> aligned = BatchAlignment.<String>align(List.of(), List.of("x"), s -> s);
        assertThat(aligned.found()).isEmpty();
        assertThat(BatchAlignment.<String>align(null, null, s -> s).missing()).isEmpty();
    }

    // ── @BatchLimit ──

    @Test
    void batchLimit_overLimit_failsFast400() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect();
        assertThatThrownBy(() -> aspect.around(joinPoint(List.of("1", "2", "3"), "runSmall")))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getType())
                .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void batchLimit_withinLimit_proceeds() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect();
        assertThatCode(() -> aspect.around(joinPoint(List.of("1", "2"), "runSmall"))).doesNotThrowAnyException();
        assertThatCode(() -> aspect.around(joinPoint(List.of("1", "2", "3"), "run"))).doesNotThrowAnyException();
    }

    @Test
    void batchLimit_topLevelList201OverDefaultLimit_failsFast400() throws Throwable {
        // 回归锚：顶层 Collection 缺省上限 200，201 条 → 400
        BatchLimitAspect aspect = new BatchLimitAspect();
        assertThatThrownBy(() -> aspect.around(joinPoint(ids(201), "runDefault"))).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getMessage()).asString().contains("201").contains("200");
    }

    @Test
    void batchLimit_recordWrapped201_failsFast400() throws Throwable {
        // 缺陷回归锚：record 包装内的 Collection 不在校验范围 → 超限静默放行；修复后逐 record 组件探测 → 400
        BatchLimitAspect aspect = new BatchLimitAspect();
        BatchUpdateRequest request = new BatchUpdateRequest(ids(201), "ATOMIC");
        assertThatThrownBy(() -> aspect.around(joinPoint(request, "runWrapped"))).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void batchLimit_recordWrappedWithinLimit_proceeds() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect();
        assertThatCode(
                () -> aspect.around(joinPoint(new BatchUpdateRequest(List.of("1", "2"), "ATOMIC"), "runWrapped")))
                .doesNotThrowAnyException();
    }

    @Test
    void batchLimit_recordWrappedSecondCollectionOverLimit_alsoFails() throws Throwable {
        // 多 Collection 组件逐一校验：首个未超、第二个超限同样 400（取第一个即判会漏报）
        BatchLimitAspect aspect = new BatchLimitAspect();
        MultiCollectionRequest request = new MultiCollectionRequest(List.of("1"), ids(201));
        assertThatThrownBy(() -> aspect.around(joinPoint(request, "runMulti"))).isInstanceOf(DomainException.class);
    }

    @Test
    void batchLimit_recordWithoutCollectionOrNullArgs_proceeds() throws Throwable {
        BatchLimitAspect aspect = new BatchLimitAspect();
        assertThatCode(() -> aspect.around(joinPoint(new BatchUpdateRequest(null, null), "runWrapped")))
                .doesNotThrowAnyException();
        assertThatCode(() -> aspect.around(joinPoint(null, "runWrapped"))).doesNotThrowAnyException();
    }

    @SuppressWarnings("unused")
    static class BatchLimitHolder {

        @BatchLimit
        void run(List<String> ids) {
        }

        @BatchLimit(max = 2)
        void runSmall(List<String> ids) {
        }

        @BatchLimit
        void runDefault(List<String> ids) {
        }

        @BatchLimit
        void runWrapped(BatchUpdateRequest request) {
        }

        @BatchLimit
        void runMulti(MultiCollectionRequest request) {
        }
    }

    /** record 包装批量请求（如 demo 的 BatchUpdateDemoTaskRequest：顶层非 Collection，批量本体藏在组件里）。 */
    record BatchUpdateRequest(List<String> ids, String mode) {
    }

    /** 多 Collection 组件 record：全部逐一校验的语义锚。 */
    record MultiCollectionRequest(List<String> ids, List<String> extraIds) {
    }

    private static List<String> ids(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count).mapToObj(String::valueOf).toList();
    }

    private ProceedingJoinPoint joinPoint(Object argument, String holderMethod) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = java.util.Arrays.stream(BatchLimitHolder.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(holderMethod)).findFirst().orElseThrow();
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getArgs()).thenReturn(new Object[]{argument});
        when(pjp.proceed()).thenReturn(new Object());
        return pjp;
    }
}
