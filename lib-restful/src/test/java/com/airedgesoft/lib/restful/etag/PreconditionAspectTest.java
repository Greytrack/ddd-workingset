package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 写请求并发版本切面单测（决策 C5/C6/C7/D13）：版本载体是 body；缺失 / 非法 → 400；删除族与 Create 豁免；批量逐条必填。
 */
class PreconditionAspectTest {

    private final PreconditionAspect aspect = new PreconditionAspect(new PreconditionProperties());

    @AfterEach
    void resetRequestContext() {
        EtagContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void patch_withLockedVersion_bindsExpectedStorageVersion() throws Throwable {
        request("/v1/demoTasks/101", "101");
        java.lang.reflect.Method patch = SampleController.class.getDeclaredMethod("patchWithId", String.class,
                UpdateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(patch, "101", new UpdateCommand(3L)))).doesNotThrowAnyException();
        // 对外代数 3 → 存储版本 2
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isEqualTo(2L);
    }

    @Test
    void patch_missingLockedVersion_rejected400() throws Throwable {
        request("/v1/demoTasks/101", "101");
        java.lang.reflect.Method patch = SampleController.class.getDeclaredMethod("patchWithId", String.class,
                UpdateCommand.class);
        assertThatThrownBy(() -> aspect.around(joinPoint(patch, "101", new UpdateCommand(null))))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getType())
                .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void patch_invalidLockedVersion_rejected400() throws Throwable {
        request("/v1/demoTasks/101", "101");
        java.lang.reflect.Method patch = SampleController.class.getDeclaredMethod("patchWithId", String.class,
                UpdateCommand.class);
        assertThatThrownBy(() -> aspect.around(joinPoint(patch, "101", new UpdateCommand(0L))))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getType())
                .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void createWithoutVersion_proceeds() throws Throwable {
        request("/v1/demoTasks", null);
        java.lang.reflect.Method create = SampleController.class.getDeclaredMethod("create", CreateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(create, new CreateCommand("T")))).doesNotThrowAnyException();
    }

    @Test
    void deleteWithoutVersion_proceeds() throws Throwable {
        // 删除族不带版本（决策 C7）：即使 body 有值也不参与并发校验
        request("/v1/demoTasks/101", "101");
        java.lang.reflect.Method delete = SampleController.class.getDeclaredMethod("delete", String.class,
                DeleteCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(delete, "101", new DeleteCommand(9L)))).doesNotThrowAnyException();
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isNull();
    }

    @Test
    void batch_allItemsVersioned_bindsEach() throws Throwable {
        java.lang.reflect.Method batch = SampleController.class.getDeclaredMethod("batchUpdate", BatchCommand.class);
        assertThatCode(() -> aspect.around(
                joinPoint(batch, new BatchCommand(List.of(new BatchItem("101", 3L), new BatchItem("102", 5L))))))
                .doesNotThrowAnyException();
        assertThat(EtagContext.expectedStorageVersion("101")).isEqualTo(2L);
        assertThat(EtagContext.expectedStorageVersion("102")).isEqualTo(4L);
    }

    @Test
    void batch_missingItemVersion_rejected400() throws Throwable {
        java.lang.reflect.Method batch = SampleController.class.getDeclaredMethod("batchUpdate", BatchCommand.class);
        assertThatThrownBy(() -> aspect.around(
                joinPoint(batch, new BatchCommand(List.of(new BatchItem("101", 3L), new BatchItem("102", null))))))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getType())
                .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void etagResourceAnnotation_bindsToDeclaredVariable() throws Throwable {
        request("/v1/demoTasks/101/steps/s1", "101");
        java.lang.reflect.Method nested = SampleController.class.getDeclaredMethod("declaredNestedPatch", String.class,
                String.class, UpdateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(nested, "101", "s1", new UpdateCommand(3L))))
                .doesNotThrowAnyException();
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isEqualTo(2L);
    }

    @Test
    void multiplePathVariables_withoutIdentityDeclaration_fails400() throws Throwable {
        request("/v1/demoTasks/101/steps/s1", "101");
        java.lang.reflect.Method nested = SampleController.class.getDeclaredMethod("nestedPatch", String.class,
                String.class, UpdateCommand.class);
        assertThatThrownBy(() -> aspect.around(joinPoint(nested, "101", "s1", new UpdateCommand(3L))))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getType())
                .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void disabledSwitch_skipsHandling() throws Throwable {
        PreconditionProperties properties = new PreconditionProperties();
        properties.setEnabled(false);
        PreconditionAspect disabled = new PreconditionAspect(properties);
        request("/v1/demoTasks/101", "101");
        java.lang.reflect.Method patch = SampleController.class.getDeclaredMethod("patchWithId", String.class,
                UpdateCommand.class);
        assertThatCode(() -> disabled.around(joinPoint(patch, "101", new UpdateCommand(null))))
                .doesNotThrowAnyException();
    }

    @Test
    void missingRequestContext_proceedsInsteadOfFailing() throws Throwable {
        java.lang.reflect.Method patch = SampleController.class.getDeclaredMethod("patchWithId", String.class,
                UpdateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(patch, "101", new UpdateCommand(3L)))).doesNotThrowAnyException();
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    }

    @Test
    void customDeleteFamilyVerb_exemptEvenIfBodyImplementsLockedCommand() throws Throwable {
        // :batchDelete / :batchRemove / :undelete 等自定义删除恢复族：即使 body 实现 LockedCommand 也豁免（P1 回归）
        request("/v1/demoTasks:batchDelete", null);
        java.lang.reflect.Method batchDelete = SampleController.class.getDeclaredMethod("batchDelete",
                UpdateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(batchDelete, new UpdateCommand(null)))).doesNotThrowAnyException();
        assertThat(EtagContext.expectedStorageVersion("demoTasks")).isNull();

        request("/v1/demoTasks:batchRemove", null);
        java.lang.reflect.Method batchRemove = SampleController.class.getDeclaredMethod("batchRemove",
                UpdateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(batchRemove, new UpdateCommand(null)))).doesNotThrowAnyException();
        assertThat(EtagContext.expectedStorageVersion("demoTasks")).isNull();

        request("/v1/demoTasks/101:undelete", "101");
        java.lang.reflect.Method undelete = SampleController.class.getDeclaredMethod("undelete", String.class,
                UpdateCommand.class);
        assertThatCode(() -> aspect.around(joinPoint(undelete, "101", new UpdateCommand(null))))
                .doesNotThrowAnyException();
    }

    // ── 样例 Controller / 命令 ──

    record UpdateCommand(Long lockedVersion) implements LockedCommand {
    }

    record CreateCommand(String title) {
    }

    record DeleteCommand(Long lockedVersion) {
    }

    record BatchItem(String id, Long lockedVersion) implements IdentifiedLockedCommand {
    }

    record BatchCommand(List<BatchItem> items) implements BatchLockedCommand {
    }

    @SuppressWarnings("unused")
    static class SampleController {

        @PatchMapping("/v1/demoTasks/{id}")
        void patchWithId(@PathVariable("id") String id, UpdateCommand command) {
        }

        @PostMapping("/v1/demoTasks")
        void create(CreateCommand command) {
        }

        @DeleteMapping("/v1/demoTasks/{id}")
        void delete(@PathVariable("id") String id, DeleteCommand command) {
        }

        @PostMapping("/v1/demoTasks:batchUpdate")
        void batchUpdate(BatchCommand command) {
        }

        @PostMapping("/v1/demoTasks:batchDelete")
        void batchDelete(UpdateCommand command) {
        }

        @PostMapping("/v1/demoTasks:batchRemove")
        void batchRemove(UpdateCommand command) {
        }

        @PostMapping("/v1/demoTasks/{id}:undelete")
        void undelete(@PathVariable("id") String id, UpdateCommand command) {
        }

        @PatchMapping("/v1/demoTasks/{id}/steps/{stepId}")
        void nestedPatch(@PathVariable("id") String id, @PathVariable("stepId") String stepId, UpdateCommand command) {
        }

        @PatchMapping("/v1/demoTasks/{id}/declaredSteps/{stepId}")
        @EtagResource("id")
        void declaredNestedPatch(@PathVariable("id") String id, @PathVariable("stepId") String stepId,
                UpdateCommand command) {
        }
    }

    private static void request(String uri, String id) {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setMethod("PATCH");
        httpRequest.setRequestURI(uri);
        if (id != null) {
            httpRequest.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of("id", id));
        }
        RequestContextHolder
                .setRequestAttributes(new ServletRequestAttributes(httpRequest, new MockHttpServletResponse()));
    }

    private ProceedingJoinPoint joinPoint(java.lang.reflect.Method sampleMethod, Object... args) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(sampleMethod);
        when(pjp.proceed()).thenReturn(new Object());
        when(pjp.getArgs()).thenReturn(args);
        return pjp;
    }
}
