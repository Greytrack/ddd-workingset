package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.etag.EtagResponseAdvice;
import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.etag.EnableRepresentationCache;
import com.airedgesoft.lib.restful.fieldtoggle.FieldToggleProvider;
import com.airedgesoft.lib.restful.pagination.Page;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ReadMaskAspect} 单测（Tier A 全局拦截 {@code ?readMask}）：返回类型白名单自动派生、未知字段 400 快速失败、{@code name} / 协议元数据恒保留、Page /
 * Collection / {@link Etagged} 信封逐条剪枝、停用预定义字段并入剪枝通道（显式引用 400）、ETag 为裁剪后表示 hash 且 304 按掩码隔离、请求间零状态残留（无线程级状态，无跨请求串号）。
 */
class ReadMaskAspectTest {

    /** 模拟读模型（{@code NON_NULL} 契约 + AIP-122 {@code name} 首字段）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QTask(String name, String taskId, String title) {
    }

    /** 基本类型组件读模型（{@code done} 为 primitive——剪枝侧恒保留）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QStep(String name, String stepId, boolean done) {
    }

    /** 含可停用预定义字段（{@code estimatedCost}）的读模型。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QCost(String name, String taskId, String title, Long estimatedCost) {
    }

    /** 协议元数据组件（{@code _access} 同型，§19.2）：不可经投影选择、恒保留。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QAccess(String name, String title, @JsonProperty("_access") String access) {
    }

    @SuppressWarnings("unused")
    static class SampleController {

        QTask get() {
            return null;
        }

        Page<QTask> list() {
            return null;
        }

        List<QTask> batchGet() {
            return null;
        }

        Optional<QTask> optionalGet() {
            return null;
        }

        ResponseEntity<QTask> entityGet() {
            return null;
        }

        QStep stepGet() {
            return null;
        }

        QAccess accessGet() {
            return null;
        }

        Etagged<QTask> enveloped() {
            return null;
        }

        QCost costGet() {
            return null;
        }

        String nonRecordGet() {
            return null;
        }

        @EnableRepresentationCache
        QTask cachedGet() {
            return null;
        }
    }

    private final EtagResponseAdvice etagAdvice = new EtagResponseAdvice();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 剪枝 ──

    @DisplayName("readMask=title：掩码内字段保留，掩码外 taskId 置 null，name 恒保留")
    @Test
    void around_maskSelectsFields_prunesOthersButKeepsName() throws Throwable {
        QTask pruned = (QTask) run("get", "title", new QTask("demoTasks/101", "101", "T"));
        // name 恒保留（AIP-122 资源名，§2 保留字）；掩码内字段保留；掩码外置 null 随 NON_NULL 消失
        assertThat(pruned.name()).isEqualTo("demoTasks/101");
        assertThat(pruned.title()).isEqualTo("T");
        assertThat(pruned.taskId()).isNull();
    }

    /** 下推使用已校验的白名单，并在控制器抛错时也释放作用域。 */
    @Test
    void around_exposesEffectiveFieldsOnlyDuringControllerInvocation() throws Throwable {
        request("title");
        ProceedingJoinPoint pjp = joinPoint("get", null);
        doAnswer(invocation -> {
            assertThat(ReadMaskAspect.currentPaths()).containsExactlyInAnyOrder("name", "title");
            throw new IllegalStateException("query failed");
        }).when(pjp).proceed();

        assertThatThrownBy(() -> aspect(null).around(pjp)).isInstanceOf(IllegalStateException.class);
        assertThat(ReadMaskAspect.currentPaths()).isEmpty();
    }

    @DisplayName("掩码未声明 name：name 仍恒保留（AIP-122 保留字），掩码外 title 置 null")
    @Test
    void around_nameAlwaysKept_evenWhenMaskExcludesIt() throws Throwable {
        QTask pruned = (QTask) run("get", "taskId", new QTask("demoTasks/101", "101", "T"));
        assertThat(pruned.name()).isEqualTo("demoTasks/101");
        assertThat(pruned.taskId()).isEqualTo("101");
        assertThat(pruned.title()).isNull();
    }

    @DisplayName("Page 信封：items 逐条剪枝且分页元数据（nextPageToken/totalSize）保留")
    @Test
    void around_pageWrapped_itemsPrunedAndEnvelopeRebuilt() throws Throwable {
        Page<QTask> page = Page
                .of(List.of(new QTask("demoTasks/101", "101", "A"), new QTask("demoTasks/102", "102", "B")), "tok", 2);
        Page<?> pruned = (Page<?>) run("list", "title", page);
        assertThat(pruned.nextPageToken()).isEqualTo("tok");
        assertThat(pruned.totalSize()).isEqualTo(2);
        assertThat(pruned.items()).allSatisfy(item -> {
            assertThat(((QTask) item).name()).isNotBlank();
            assertThat(((QTask) item).title()).isNotNull();
            assertThat(((QTask) item).taskId()).isNull();
        });
    }

    @DisplayName("Collection 信封：含 null 元素的稀疏列表逐条剪枝且 null 元素原样保留")
    @Test
    void around_collectionWrapped_sparseItemsPruned() throws Throwable {
        List<QTask> sparse = Arrays.asList(new QTask("demoTasks/101", "101", "A"), null);
        @SuppressWarnings("unchecked")
        List<QTask> pruned = (List<QTask>) run("batchGet", "title", sparse);
        assertThat(pruned.get(0).taskId()).isNull();
        assertThat(pruned.get(0).title()).isEqualTo("A");
        assertThat(pruned.get(1)).isNull();
    }

    @DisplayName("Etagged 信封：lockedVersion 保留，resource 按掩码剪枝")
    @Test
    void around_etaggedEnvelope_envelopeRebuiltKeepingLockedVersion() throws Throwable {
        Etagged<?> pruned = (Etagged<?>) run("enveloped", "title",
                Etagged.of(new QTask("demoTasks/101", "101", "T"), 7L));
        assertThat(pruned.lockedVersion()).isEqualTo(7L);
        // lockedVersion 是协议信封 wire 注入字段：剪枝发生在信封装配前，天然不受掩码影响
        assertThat(((QTask) pruned.resource()).taskId()).isNull();
        assertThat(((QTask) pruned.resource()).title()).isEqualTo("T");
    }

    @DisplayName("Optional/ResponseEntity 载荷：内层读模型剪枝且响应状态不受影响")
    @Test
    void around_optionalAndEntityPayloads_innerValuePruned() throws Throwable {
        Optional<?> pruned = (Optional<?>) run("optionalGet", "title",
                Optional.of(new QTask("demoTasks/101", "101", "T")));
        assertThat(pruned).hasValueSatisfying(v -> assertThat(((QTask) v).taskId()).isNull());
        @SuppressWarnings("unchecked")
        ResponseEntity<QTask> entity = (ResponseEntity<QTask>) run("entityGet", "title",
                ResponseEntity.ok(new QTask("demoTasks/101", "101", "T")));
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().taskId()).isNull();
    }

    @DisplayName("基本类型组件（done）：不可置 null，随 name 一并恒保留")
    @Test
    void around_primitiveComponent_keptRegardlessOfMask() throws Throwable {
        QStep pruned = (QStep) run("stepGet", "done", new QStep("demoTasks/101/taskSteps/9", "9", true));
        // 基本类型不可置 null（NON_NULL 无法省略）→ 与 name 一并恒保留
        assertThat(pruned.done()).isTrue();
        assertThat(pruned.name()).isNotBlank();
        assertThat(pruned.stepId()).isNull();
    }

    @DisplayName("协议元数据（_access 同型）：不可经掩码选择（显式选择 400）但合法掩码下恒不被剪除")
    @Test
    void around_protocolMetadata_notSelectableButAlwaysKept() throws Throwable {
        // 元数据组件（_access 同型）不入白名单：显式选择 → 未知字段 400
        assertThatThrownBy(() -> run("accessGet", "access", new QAccess("demoTasks/101", "T", "_acc")))
                .isInstanceOf(DomainException.class).extracting("resCode").isEqualTo(DomainRestCode.READ_MASK_UNKNOWN);
        // 合法掩码：元数据不被剪除（伴随节点留痕语义）
        QAccess pruned = (QAccess) run("accessGet", "title", new QAccess("demoTasks/101", "T", "_acc"));
        assertThat(pruned.access()).isEqualTo("_acc");
        assertThat(pruned.title()).isEqualTo("T");
    }

    @DisplayName("readMask 缺席或空白：原实例透传，不发生剪枝")
    @Test
    void around_noMaskOrBlank_passesThroughSameInstance() throws Throwable {
        QTask body = new QTask("demoTasks/101", "101", "T");
        assertThat(run("get", null, body)).isSameAs(body);
        assertThat(run("get", " ", body)).isSameAs(body);
    }

    @DisplayName("非 record 返回类型（String）：即使携带掩码也不剪枝，原实例透传")
    @Test
    void around_nonRecordReturnType_ignoredEvenWithMask() throws Throwable {
        String body = "hello";
        assertThat(run("nonRecordGet", "title", body)).isSameAs(body);
    }

    // ── 校验（400 快速失败，方法体不执行） ──

    @DisplayName("未知字段：方法体执行前快速失败 400（READ_MASK_UNKNOWN），fieldViolations 指明未知字段")
    @Test
    void around_unknownField_failsFast400BeforeMethodExecution() throws Throwable {
        boolean[] proceeded = {false};
        ProceedingJoinPoint pjp = joinPoint("get", new QTask("demoTasks/101", "101", "T"), proceeded);
        request("typo");
        assertThatThrownBy(() -> aspect(null).around(pjp)).isInstanceOf(DomainException.class).satisfies(ex -> {
            DomainException domain = (DomainException) ex;
            assertThat(domain.getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
            assertThat(domain.getResCode()).isEqualTo(DomainRestCode.READ_MASK_UNKNOWN);
            assertThat(domain.getFieldViolations().stream().map(v -> v.field())).containsExactly("typo");
        });
        assertThat(proceeded[0]).isFalse();
    }

    // ── 停用预定义字段（fieldtoggle SPI 并入剪枝通道） ──

    private static FieldToggleProvider demoTasksToggles() {
        return collection -> "demoTasks".equals(collection) ? Set.of("estimatedCost") : Set.of();
    }

    @DisplayName("显式请求停用字段：400（PREDEFINED_FIELD_DISABLED），fieldViolations 指明该字段")
    @Test
    void around_disabledField_explicitRequest_throws400FieldUnavailable() throws Throwable {
        assertThatThrownBy(
                () -> run("costGet", "estimatedCost", new QCost("demoTasks/101", "101", "T", 42L), demoTasksToggles()))
                .isInstanceOf(DomainException.class).satisfies(ex -> {
                    DomainException domain = (DomainException) ex;
                    assertThat(domain.getResCode()).isEqualTo(DomainRestCode.PREDEFINED_FIELD_DISABLED);
                    assertThat(domain.getFieldViolations().stream().map(v -> v.field()))
                            .containsExactly("estimatedCost");
                });
    }

    @DisplayName("停用字段并入剪枝通道：显式掩码下未被请求的停用字段（estimatedCost）也被剔除")
    @Test
    void around_disabledFields_mergedIntoPruneChannel() throws Throwable {
        // 显式掩码：掩码剪枝 ∪ 停用剔除——estimatedCost 未被请求也因停用而消失
        QCost pruned = (QCost) run("costGet", "title", new QCost("demoTasks/101", "101", "T", 42L), demoTasksToggles());
        assertThat(pruned.name()).isEqualTo("demoTasks/101");
        assertThat(pruned.title()).isEqualTo("T");
        assertThat(pruned.taskId()).isNull();
        assertThat(pruned.estimatedCost()).isNull();
    }

    @DisplayName("无掩码请求：仅剔除停用字段，其余业务字段原样保留")
    @Test
    void around_disabledFields_withoutMask_pruneOnlyDisabledFields() throws Throwable {
        // 未请求投影（无 readMask）：仅停用字段剔除，其余字段原样保留
        QCost pruned = (QCost) run("costGet", null, new QCost("demoTasks/101", "101", "T", 42L), demoTasksToggles());
        assertThat(pruned.estimatedCost()).isNull();
        assertThat(pruned.taskId()).isEqualTo("101");
        assertThat(pruned.title()).isEqualTo("T");
    }

    @DisplayName("FieldToggleProvider Bean 缺席：开关剔除静默失效，投影仍按掩码生效（零开销缺省）")
    @Test
    void around_providerAbsent_toggleSilentlyInactive() throws Throwable {
        // 无 FieldToggleProvider Bean：读投影与开关剔除全部静默放行（零开销缺省）
        QTask pruned = (QTask) run("get", "title", new QTask("demoTasks/101", "101", "T"), null);
        assertThat(pruned.taskId()).isNull();
        QTask untouched = (QTask) run("get", null, new QTask("demoTasks/101", "101", "T"), null);
        assertThat(untouched.taskId()).isEqualTo("101");
    }

    @DisplayName("停用注册表按集合键控：其他集合的停用字段不波及本端点")
    @Test
    void around_disabledFields_otherCollection_notApplied() throws Throwable {
        // 停用注册表按集合键控：其他集合的停用字段不波及本端点
        FieldToggleProvider provider = collection -> "otherTasks".equals(collection) ? Set.of("title") : Set.of();
        QCost pruned = (QCost) run("costGet", null, new QCost("demoTasks/101", "101", "T", 42L), provider);
        assertThat(pruned.title()).isEqualTo("T");
    }

    @DisplayName("停用字段为基本类型组件（done）：剪枝通道豁免基本类型，不触发重建失败照常返回")
    @Test
    void around_disabledPrimitiveComponent_keptWithoutRebuildFailure() throws Throwable {
        // BC 把基本类型组件（boolean done）声明停用：置 null 不可行 → 剪枝通道豁免（与 name 同策略），不抛重建 IllegalStateException
        FieldToggleProvider provider = collection -> "demoTasks".equals(collection) ? Set.of("done") : Set.of();
        QStep pruned = (QStep) run("stepGet", null, new QStep("demoTasks/101/taskSteps/9", "9", true), provider);
        assertThat(pruned.done()).isTrue();
        assertThat(pruned.name()).isEqualTo("demoTasks/101/taskSteps/9");
        assertThat(pruned.stepId()).isEqualTo("9");
    }

    // ── 请求隔离（无线程级状态：无 ThreadLocal 清理负担，请求上下文由容器管理） ──

    @DisplayName("请求隔离：连续请求无掩码串号，无请求上下文的裸线程原样透传")
    @Test
    void around_consecutiveRequests_noStateLeak() throws Throwable {
        assertThat(((QTask) run("get", "title", new QTask("demoTasks/101", "101", "T"))).taskId()).isNull();
        // 下一请求（无掩码）不受上一请求掩码影响
        QTask next = (QTask) run("get", null, new QTask("demoTasks/101", "101", "T"));
        assertThat(next.taskId()).isEqualTo("101");
        // 无请求上下文（线程池复用后的裸线程 / 定时作业）→ 原样透传，不残留任何上下文
        RequestContextHolder.resetRequestAttributes();
        QTask body = new QTask("demoTasks/101", "101", "T");
        assertThat(aspect(null).around(joinPoint("get", body))).isSameAs(body);
    }

    // ── ETag 一致性（hash 为裁剪后表示 hash；304 按掩码隔离） ──

    @DisplayName("ETag 为裁剪后表示 hash：同掩码恒定、掩码变化而变化、无掩码等同全覆盖掩码")
    @Test
    void around_etagHash_variesByMask_andStablePerMask() throws Throwable {
        QTask body = new QTask("demoTasks/101", "101", "T");
        String etagTitle = etagOf(run("cachedGet", "title", body), "cachedGet");
        // 同一掩码 → 同一裁剪表示 → 恒同一 hash
        assertThat(etagOf(run("cachedGet", "title", body), "cachedGet")).isEqualTo(etagTitle);
        // 掩码变化 → 裁剪表示变化 → hash 变化
        String etagTitleAndId = etagOf(run("cachedGet", "title,taskId", body), "cachedGet");
        assertThat(etagTitleAndId).isNotEqualTo(etagTitle);
        // 无掩码 = 全量表示：与覆盖全部白名单字段的掩码同一表示（AIP-157 掩码全覆盖），hash 一致
        assertThat(etagOf(run("cachedGet", null, body), "cachedGet")).isNotEqualTo(etagTitle).isEqualTo(etagTitleAndId);
    }

    @DisplayName("304 按掩码隔离：同掩码 If-None-Match 命中 304，跨掩码复用恒 200")
    @Test
    void around_enableRepresentationCache_304IsolatedPerMask() throws Throwable {
        // 首次：掩码 title → 200 + 表示 hash ETag
        String etagTitle = etagOf(run("cachedGet", "title", new QTask("demoTasks/101", "101", "T")), "cachedGet");
        assertThat(etagTitle).startsWith("\"sha256-");
        // 同掩码 + If-None-Match 命中 → 304（无 body），ETag 恒定
        request("title", etagTitle);
        MockHttpServletResponse notModified = adviceCall(
                aspect(null).around(joinPoint("get", new QTask("demoTasks/101", "101", "T"))), "cachedGet");
        assertThat(notModified.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED.value());
        assertThat(notModified.getHeader("ETag")).isEqualTo(etagTitle);
        // 跨掩码复用 If-None-Match：表示 hash 不同 → 恒 200（304 语义不被掩码破坏）
        request("taskId", etagTitle);
        MockHttpServletResponse full = adviceCall(
                aspect(null).around(joinPoint("get", new QTask("demoTasks/101", "101", "T"))), "cachedGet");
        assertThat(full.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(full.getHeader("ETag")).isNotEqualTo(etagTitle);
    }

    // ── 辅助 ──

    private Object run(String endpoint, String readMask, Object body) throws Throwable {
        return run(endpoint, readMask, body, null);
    }

    private Object run(String endpoint, String readMask, Object body, FieldToggleProvider provider) throws Throwable {
        request(readMask, null);
        return aspect(provider).around(joinPoint(endpoint, body));
    }

    private static void request(String readMask, String ifNoneMatch) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/v1/demoTasks/101");
        if (readMask != null) {
            request.setParameter("readMask", readMask);
        }
        if (ifNoneMatch != null) {
            request.addHeader("If-None-Match", ifNoneMatch);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }

    private static void request(String readMask) {
        request(readMask, null);
    }

    private ReadMaskAspect aspect(FieldToggleProvider provider) {
        @SuppressWarnings("unchecked")
        ObjectProvider<FieldToggleProvider> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(provider);
        return new ReadMaskAspect(objectProvider);
    }

    private ProceedingJoinPoint joinPoint(String endpoint, Object body) throws Throwable {
        return joinPoint(endpoint, body, new boolean[1]);
    }

    private ProceedingJoinPoint joinPoint(String endpoint, Object body, boolean[] proceeded) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method(endpoint));
        doAnswer(invocation -> {
            proceeded[0] = true;
            return body;
        }).when(pjp).proceed();
        return pjp;
    }

    private String etagOf(Object prunedBody, String endpoint) {
        return adviceCall(prunedBody, endpoint).getHeader("ETag");
    }

    /** 剪枝结果送入 {@link EtagResponseAdvice}（模拟真实响应装配链：aspect → advice）。 */
    private MockHttpServletResponse adviceCall(Object prunedBody, String endpoint) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        etagAdvice.beforeBodyWrite(prunedBody, parameter(endpoint), MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class, new ServletServerHttpRequest(currentRequest()),
                new ServletServerHttpResponse(response));
        return response;
    }

    private static MockHttpServletRequest currentRequest() {
        return (MockHttpServletRequest) ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
    }

    private static Method method(String name) {
        try {
            return SampleController.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MethodParameter parameter(String method) {
        return new MethodParameter(method(method), -1);
    }
}
