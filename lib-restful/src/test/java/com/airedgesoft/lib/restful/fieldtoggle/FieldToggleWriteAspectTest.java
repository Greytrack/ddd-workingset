package com.airedgesoft.lib.restful.fieldtoggle;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.updatemask.UpdateMask;
import com.airedgesoft.lib.restful.updatemask.UpdateMaskAspect;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FieldToggleWriteAspect} 单测（Tier B 写路径收口）：updateMask 引用停用预定义字段 → 400 + 逐字段 {@code fieldViolations}
 * （字段不存在语义，方法体不执行）；与 {@link UpdateMaskAspect} 校验链协作（复用其已解析掩码请求属性）；无停用 / 其他集合 / 无掩码 → 放行。
 */
class FieldToggleWriteAspectTest {

    @SuppressWarnings("unused")
    static class SampleWriteController {

        @ValidUpdateMask(whitelist = {"title", "description", "estimatedCost"})
        Object patch() {
            return new Object();
        }

        @ValidUpdateMask(whitelist = {"title"})
        Object patchOther() {
            return new Object();
        }
    }

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @DisplayName("停用拦截：updateMask 引用预定义停用字段 → 400 INVALID_ARGUMENT 并逐字段 fieldViolations，方法体不执行")
    @Test
    void around_maskReferencesDisabledField_failsFast400WithViolations() throws Throwable {
        boolean[] proceeded = {false};
        request("/v1/demoTasks/101");
        bindParsedMask("title,estimatedCost", "title,estimatedCost");
        ProceedingJoinPoint pjp = joinPoint("patch", proceeded);
        assertThatThrownBy(() -> aspect(Set.of("estimatedCost")).around(pjp)).isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException domain = (DomainException) ex;
                    assertThat(domain.getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
                    assertThat(domain.getResCode()).isEqualTo(DomainRestCode.PREDEFINED_FIELD_DISABLED);
                    assertThat(domain.getFieldViolations().stream().map(v -> v.field()))
                            .containsExactly("estimatedCost");
                });
        // 方法体不执行（校验链在 @ValidUpdateMask 静态白名单之后补停用裁决）
        assertThat(proceeded[0]).isFalse();
    }

    @DisplayName("停用放行：updateMask 未引用停用字段 → 方法体照常执行")
    @Test
    void around_maskWithoutDisabledField_proceeds() throws Throwable {
        boolean[] proceeded = {false};
        request("/v1/demoTasks/101");
        bindParsedMask("title,description", "title,description");
        Object out = aspect(Set.of("estimatedCost")).around(joinPoint("patch", proceeded));
        assertThat(proceeded[0]).isTrue();
        assertThat(out).isNotNull();
    }

    @DisplayName("停用放行：当前集合停用声明为空集 → 方法体照常执行")
    @Test
    void around_disabledEmptyForCollection_proceeds() throws Throwable {
        request("/v1/demoTasks/101");
        bindParsedMask("estimatedCost", "estimatedCost");
        // 停用注册表按集合键控：demoTasks 无停用字段 → 放行
        boolean[] proceeded = {false};
        aspect(Set.of()).around(joinPoint("patch", proceeded));
        assertThat(proceeded[0]).isTrue();
    }

    @DisplayName("集合隔离：停用字段声明在其他集合 → 不作用于本端点，照常放行")
    @Test
    void around_otherCollectionDisabled_notApplied() throws Throwable {
        request("/v1/demoTasks/101");
        bindParsedMask("estimatedCost", "estimatedCost");
        // 停用声明在其他集合（otherTasks）→ 本端点放行
        boolean[] proceeded = {false};
        aspectFor("otherTasks", Set.of("estimatedCost")).around(joinPoint("patchOther", proceeded));
        assertThat(proceeded[0]).isTrue();
    }

    @DisplayName("冒号动词：集合级自定义方法 URI 正确派生集合键，停用字段仍拦截为 400")
    @Test
    void around_collectionLevelColonVerb_collectionDerivedCorrectly() throws Throwable {
        request("/v1/demoTasks:batchUpdate");
        bindParsedMask("estimatedCost", "estimatedCost");
        // 停用注册表按集合键控：仅 demoTasks 声明停用——派生错误（集合键不落在 demoTasks）本用例即失败
        assertThatThrownBy(
                () -> aspectFor("demoTasks", Set.of("estimatedCost")).around(joinPoint("patch", new boolean[1])))
                .isInstanceOf(DomainException.class).extracting("resCode")
                .isEqualTo(DomainRestCode.PREDEFINED_FIELD_DISABLED);
    }

    @DisplayName("停用放行：请求缺少已解析掩码属性（校验链未启用）→ 不做二次裁决照常执行")
    @Test
    void around_maskAttributeAbsent_proceeds() throws Throwable {
        // 无已解析掩码（UpdateMaskAspect 未执行——如切面链未启用）→ 本切面不做二次裁决
        request("/v1/demoTasks/101");
        boolean[] proceeded = {false};
        assertThatCode(() -> aspect(Set.of("estimatedCost")).around(joinPoint("patch", proceeded)))
                .doesNotThrowAnyException();
        assertThat(proceeded[0]).isTrue();
    }

    @DisplayName("停用放行：无 HTTP 请求上下文（单测直调 / 定时作业）→ 原样放行不抛异常")
    @Test
    void around_noRequestContext_proceeds() throws Throwable {
        // 无 HTTP 上下文（单测直调 / 定时作业）→ 原样放行（与 UpdateMaskAspect 语义一致）
        assertThatCode(() -> aspect(Set.of("estimatedCost")).around(joinPoint("patch", new boolean[1])))
                .doesNotThrowAnyException();
    }

    @DisplayName("停用放行：停用字段提供方返回 null → 视为无停用声明照常执行")
    @Test
    void around_providerReturnsNull_proceeds() throws Throwable {
        request("/v1/demoTasks/101");
        bindParsedMask("title", "title");
        boolean[] proceeded = {false};
        assertThatCode(() -> aspect(null).around(joinPoint("patch", proceeded))).doesNotThrowAnyException();
        assertThat(proceeded[0]).isTrue();
    }

    // ── 辅助 ──

    private FieldToggleWriteAspect aspect(Set<String> disabled) {
        return new FieldToggleWriteAspect(collection -> disabled);
    }

    /** 停用注册表按集合键控的模拟实现：仅声明集合返回停用集，其余集合空。 */
    private FieldToggleWriteAspect aspectFor(String declaredCollection, Set<String> disabled) {
        return new FieldToggleWriteAspect(collection -> declaredCollection.equals(collection) ? disabled : Set.of());
    }

    private static void request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("PATCH");
        request.setRequestURI(uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }

    /**
     * 模拟校验链上游：{@code UpdateMaskAspect}（+16）解析原始 query 参数并把结果挂同名请求属性（本切面经 {@code currentMask()} 复用； 模式判定读原始 query
     * 参数——省略掩码 = 不设参数）。
     */
    private static void bindParsedMask(String rawQuery, String resolved) {
        MockHttpServletRequest request = (MockHttpServletRequest) ((ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes()).getRequest();
        if (rawQuery != null) {
            request.setParameter("updateMask", rawQuery);
        }
        request.setAttribute(UpdateMaskAspect.REQUEST_ATTRIBUTE, UpdateMask.parse(resolved));
    }

    private ProceedingJoinPoint joinPoint(String endpoint, boolean[] proceeded) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(SampleWriteController.class.getDeclaredMethod(endpoint));
        doAnswer(invocation -> {
            proceeded[0] = true;
            return new Object();
        }).when(pjp).proceed();
        return pjp;
    }

    @DisplayName("`*` 全量替换：候选集剔除停用字段后放行（§20「字段不存在」；否则部署期一停用即任何 * PATCH 恒 400）")
    @Test
    void around_wildcardMask_dropsDisabledFieldsAndProceeds() throws Throwable {
        boolean[] proceeded = {false};
        request("/v1/demoTasks/101");
        // 模拟 `*`：原始 query 参数为 `*`，已归一掩码为白名单全集（UpdateMaskAspect 展开）
        bindParsedMask("*", "title,description,estimatedCost");
        ProceedingJoinPoint pjp = joinPoint("patch", proceeded);
        assertThatCode(() -> aspect(Set.of("estimatedCost")).around(pjp)).doesNotThrowAnyException();
        assertThat(proceeded[0]).isTrue();
        // 下游命令服务读到的掩码不含停用字段
        UpdateMask narrowed = UpdateMaskAspect.currentMask();
        assertThat(narrowed.paths()).containsExactly("title", "description");
    }

    @DisplayName("省略掩码：候选集同样剔除停用字段后放行（与 `*` 及读侧「省略=静默剪枝」同口径，写侧两态统一）")
    @Test
    void around_omittedMask_dropsDisabledFieldsAndProceeds() throws Throwable {
        boolean[] proceeded = {false};
        request("/v1/demoTasks/101");
        // 模拟省略掩码：原始 query 不带 updateMask，已归一掩码为 body 已填充的白名单字段（UpdateMaskAspect 展开）
        bindParsedMask(null, "title,estimatedCost");
        ProceedingJoinPoint pjp = joinPoint("patch", proceeded);
        assertThatCode(() -> aspect(Set.of("estimatedCost")).around(pjp)).doesNotThrowAnyException();
        assertThat(proceeded[0]).isTrue();
        // 下游命令服务读到的掩码不含停用字段
        UpdateMask narrowed = UpdateMaskAspect.currentMask();
        assertThat(narrowed.paths()).containsExactly("title");
    }
}
