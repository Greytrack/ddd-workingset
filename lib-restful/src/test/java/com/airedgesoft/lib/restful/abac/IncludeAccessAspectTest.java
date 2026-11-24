package com.airedgesoft.lib.restful.abac;

import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code _access} 伴随切面单测（组件 8；脱敏恒生效 / includeAccess 挂载 / 缺省 Provider 向后兼容 / 脱敏下沉集合端点）。
 */
class IncludeAccessAspectTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 单资源（Etagged）──

    @Test
    @SuppressWarnings("unchecked")
    void maskedFields_alwaysAppliedEvenWithoutIncludeAccess() throws Throwable {
        request(null);
        Object body = aspect(provider(Set.of("estimatedCost"), Set.of())).around(joinPoint());
        QDemoTask resource = ((Etagged<QDemoTask>) body).resource();
        assertThat(resource.estimatedCost()).isNull(); // Omit 恒生效
        assertThat(resource.access()).isNull(); // includeAccess 未开 → 不挂节点
    }

    @Test
    @SuppressWarnings("unchecked")
    void includeAccessTrue_attachesAccessNode() throws Throwable {
        request("true");
        Object body = aspect(provider(Set.of("estimatedCost"), Set.of("title"))).around(joinPoint());
        QDemoTask resource = ((Etagged<QDemoTask>) body).resource();
        assertThat(resource.estimatedCost()).isNull();
        assertThat(resource.access()).isNotNull();
        assertThat(resource.access().writableFields()).containsExactly("title");
        assertThat(resource.access().maskedFields()).containsExactly("estimatedCost");
    }

    @Test
    void permitAllDefault_noMaskNoNode_passesThroughUnchanged() throws Throwable {
        request("false");
        Etagged<QDemoTask> original = Etagged.of(sample(), 3L);
        Object body = aspect(ctx -> AccessMetadata.allowAll()).around(joinPointOf(original, "endpoint"));
        assertThat(body).isSameAs(original); // 向后兼容：零脱敏零节点时不重建响应
    }

    @Test
    void resourceName_drivesPolicyContext() throws Throwable {
        request("true");
        final AccessContext[] captured = new AccessContext[1];
        AccessPolicyProvider recording = ctx -> {
            captured[0] = ctx;
            return AccessMetadata.allowAll();
        };
        aspect(recording).around(joinPoint());
        assertThat(captured[0].resourceType()).isEqualTo("demoTasks");
        assertThat(captured[0].resourceId()).isEqualTo("101");
    }

    // ── Page 条目（脱敏下沉集合端点）──

    @Test
    @SuppressWarnings("unchecked")
    void page_itemsAlwaysMasked_andNeverCarryAccessNode() throws Throwable {
        request("true"); // 集合端点即便误传 includeAccess 也不得挂节点（§19.2：_access 不随列表下发）
        QDemoTask second = new QDemoTask("demoTasks/102", "102", "法兰盘", 888888L, 4L, null);
        Page<QDemoTask> original = Page.of(List.of(sample(), second), "token-9", 2);
        Object body = aspect(provider(Set.of("estimatedCost"), Set.of())).around(joinPointOf(original, "pageEndpoint"));
        Page<QDemoTask> page = (Page<QDemoTask>) body;
        assertThat(page).isNotSameAs(original); // 有条目被掩 → 重建 Page
        assertThat(page.items()).extracting(QDemoTask::estimatedCost).containsExactly(null, null); // Omit 恒生效
        assertThat(page.items()).extracting(QDemoTask::access).containsExactly(null, null); // 不挂 _access
        assertThat(page.nextPageToken()).isEqualTo("token-9"); // 游标 / 总数原样保留
        assertThat(page.totalSize()).isEqualTo(2);
        assertThat(original.items().get(0).estimatedCost()).isEqualTo(999999L); // 原 Page 不可变语义不被触碰
    }

    @Test
    @SuppressWarnings("unchecked")
    void page_rowsAdjudicatedIndividually_byResourceId() throws Throwable {
        request(null);
        // 行级裁决：仅 102 行脱敏（AccessPolicyProvider javadoc：集合行过滤由宿主按行调用）
        AccessPolicyProvider rowLevel = ctx -> "102".equals(ctx.resourceId())
                ? new AccessMetadata(Set.of(), Set.of(), Set.of("estimatedCost"), java.util.Map.of())
                : AccessMetadata.allowAll();
        Page<QDemoTask> original = Page.of(List.of(sample(), ofId("102")), null, 2);
        Object body = aspect(rowLevel).around(joinPointOf(original, "pageEndpoint"));
        Page<QDemoTask> page = (Page<QDemoTask>) body;
        assertThat(page.items().get(0).estimatedCost()).isEqualTo(999999L); // 101 行不脱敏
        assertThat(page.items().get(1).estimatedCost()).isNull(); // 102 行脱敏
    }

    @Test
    void page_nothingMasked_passesThroughUnchanged() throws Throwable {
        request(null);
        Page<QDemoTask> original = Page.of(List.of(sample()), "t", 1);
        Object body = aspect(ctx -> AccessMetadata.allowAll()).around(joinPointOf(original, "pageEndpoint"));
        assertThat(body).isSameAs(original); // 零脱敏不重建（向后兼容）
    }

    // ── Collection 条目（:batchGet 稀疏列表）──

    @Test
    @SuppressWarnings("unchecked")
    void collection_attachableElementsMasked_nonAttachableUntouched() throws Throwable {
        request(null);
        List<Object> sparse = new ArrayList<>(List.of(sample(), "not-a-payload", ofId("102")));
        List<Object> originalSnapshot = List.copyOf(sparse);
        Object body = aspect(provider(Set.of("estimatedCost"), Set.of())).around(joinPointOf(sparse, "listEndpoint"));
        List<Object> list = (List<Object>) body;
        assertThat(list).isNotSameAs(sparse); // 有元素被掩 → 重建副本（保迭代序）
        assertThat(list).hasSize(3); // 稀疏列表缺失位不补
        assertThat(((QDemoTask) list.get(0)).estimatedCost()).isNull(); // 载荷条目脱敏
        assertThat(list.get(1)).isEqualTo("not-a-payload"); // 非 AccessAttachable 元素原样
        assertThat(((QDemoTask) list.get(2)).estimatedCost()).isNull();
        assertThat(originalSnapshot.get(0)).isEqualTo(sparse.get(0)); // 原列表元素不被触碰
        assertThat(((QDemoTask) originalSnapshot.get(0)).estimatedCost()).isEqualTo(999999L);
    }

    @Test
    void collection_nothingMasked_passesThroughUnchanged() throws Throwable {
        request(null);
        List<QDemoTask> original = List.of(sample());
        Object body = aspect(ctx -> AccessMetadata.allowAll()).around(joinPointOf(original, "listEndpoint"));
        assertThat(body).isSameAs(original);
    }

    // ── 测试样例 ──

    /** Q* record 法定形态：实现 {@link AccessAttachable}，{@code @JsonProperty("_access")} 挂载点。 */
    record QDemoTask(String name, String taskId, String title, Long estimatedCost, Long lockedVersion,
            AccessNode access) implements AccessAttachable<QDemoTask> {

        @Override
        public QDemoTask withAccess(AccessNode access) {
            return new QDemoTask(name, taskId, title, estimatedCost, lockedVersion, access);
        }
    }

    private QDemoTask sample() {
        return new QDemoTask("demoTasks/101", "101", "齿轮箱", 999999L, 3L, null);
    }

    private QDemoTask ofId(String id) {
        return new QDemoTask("demoTasks/" + id, id, "任务" + id, 777777L, 2L, null);
    }

    private IncludeAccessAspect aspect(AccessPolicyProvider provider) {
        return new IncludeAccessAspect(provider);
    }

    private static AccessPolicyProvider provider(Set<String> masked, Set<String> writable) {
        return ctx -> new AccessMetadata(Set.of(), writable, masked, java.util.Map.of());
    }

    private static void request(String includeAccess) {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getParameter("includeAccess")).thenReturn(includeAccess);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest, httpResponse));
    }

    private org.aspectj.lang.ProceedingJoinPoint joinPoint() throws Throwable {
        return joinPointOf(Etagged.of(sample(), 3L), "endpoint");
    }

    private org.aspectj.lang.ProceedingJoinPoint joinPointOf(Object result, String holderMethod) throws Throwable {
        org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(IncludeAccessAspectTest.class.getDeclaredMethod(holderMethod));
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }

    @SuppressWarnings("unused")
    private Etagged<QDemoTask> endpoint() {
        return null;
    }

    @SuppressWarnings("unused")
    private Page<QDemoTask> pageEndpoint() {
        return null;
    }

    @SuppressWarnings("unused")
    private List<QDemoTask> listEndpoint() {
        return null;
    }

    // ── 单例资源（AIP-156：name 无 '/'，靠载荷自声明 AccessAttachable 识别为资源载荷）──

    /** 单例读模型：{@code name} 就是集合名（无 ID 段）。 */
    record QSettings(String name, String theme) implements AccessAttachable<QSettings> {

        @Override
        public QSettings withAccess(AccessNode access) {
            return this;
        }
    }

    /** 未自声明 {@link AccessAttachable} 的同形载荷：即便 name 形似单例资源名也不裁决。 */
    record QPlainEnvelope(String name, String theme) {
    }

    @Test
    void accessAttachablePayload_nameWithoutSlash_isMasked() throws Throwable {
        request(null);
        QSettings body = (QSettings) aspect(
                ctx -> new AccessMetadata(Set.of(), Set.of(), Set.of("theme"), java.util.Map.of()))
                .around(joinPointForTarget(new Object(), new QSettings("demoSettings", "dark")));
        assertThat(body.theme()).isNull(); // §19.5 法定形态自声明参与脱敏（单例无 '/' 资源名的入口，AIP-156）
    }

    @Test
    void plainPayload_nameWithoutSlash_isTreatedAsNonResource() throws Throwable {
        request(null);
        QPlainEnvelope body = (QPlainEnvelope) aspect(
                ctx -> new AccessMetadata(Set.of(), Set.of(), Set.of("theme"), java.util.Map.of()))
                .around(joinPointForTarget(new Object(), new QPlainEnvelope("demoSettings", "dark")));
        assertThat(body.theme()).isEqualTo("dark"); // 未实现 AccessAttachable 且 name 无 '/' → 非资源载荷，不误脱敏
    }

    @Test
    void routing_usesLightPathsWithoutActionsAndBatchDeduplication() throws Throwable {
        AccessPolicyProvider spyProvider = mock(AccessPolicyProvider.class);
        when(spyProvider.resolveWithoutActions(any())).thenReturn(AccessMetadata.allowAll());
        when(spyProvider.resolve(any())).thenReturn(AccessMetadata.allowAll());
        when(spyProvider.resolveBatchForMasking(anyList())).thenReturn(java.util.Map.of());

        request(null);
        aspect(spyProvider).around(joinPoint());
        verify(spyProvider, times(1)).resolveWithoutActions(new AccessContext("demoTasks", "101"));
        verify(spyProvider, never()).resolve(any());

        request("true");
        aspect(spyProvider).around(joinPoint());
        verify(spyProvider, times(1)).resolve(new AccessContext("demoTasks", "101"));

        request(null);
        Page<QDemoTask> duplicatedPage = Page.of(List.of(sample(), sample(), ofId("102")), null, 3);
        aspect(spyProvider).around(joinPointOf(duplicatedPage, "pageEndpoint"));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<AccessContext>> batchCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(spyProvider, times(1)).resolveBatchForMasking(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).containsExactly(new AccessContext("demoTasks", "101"),
                new AccessContext("demoTasks", "102"));
    }

    private org.aspectj.lang.ProceedingJoinPoint joinPointForTarget(Object target, Object result) throws Throwable {
        org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);
        when(signature.getMethod()).thenReturn(Object.class.getDeclaredMethod("toString"));
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }
}
