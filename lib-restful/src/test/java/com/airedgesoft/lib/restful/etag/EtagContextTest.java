package com.airedgesoft.lib.restful.etag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求级并发上下文 {@link EtagContext} 单测：body {@code lockedVersion} 期望绑定（代数 N → 存储版本 N-1）、加载/保存观测、 代数换算出口、非 HTTP 编程式作用域。并发只依赖
 * body {@code lockedVersion}，{@code If-Match} 头入口已移除。
 */
class EtagContextTest {

    @AfterEach
    void reset() {
        EtagContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 期望绑定 ──

    @Test
    void bindLockedVersion_parsesGenerationToStorageVersion() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 3L);
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isEqualTo(2L);
        assertThat(EtagContext.expectedStorageVersion("demoTasks/102")).isNull(); // 按资源键控，不外溢
    }

    @Test
    void bindLockedVersion_rebindOverwrites() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 3L);
        EtagContext.bindLockedVersion("demoTasks/101", 4L);
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isEqualTo(3L);
    }

    // ── 期望消费（一次写对应一次期望）──

    @Test
    void recordSaved_consumesExpectation_reloadSkipsStaleAssertion() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 1L);
        // 保存前加载：期望 0 与实际 0 一致
        assertThatCode(() -> EtagContext.recordLoaded("demoTasks/101", new Object(), 0L)).doesNotThrowAnyException();
        // 保存推进到 1；同请求写后重读（响应组装）看到新版本属合法——不再以陈旧期望断言
        EtagContext.recordSaved("demoTasks/101", new Object(), 1L);
        assertThatCode(() -> EtagContext.recordLoaded("demoTasks/101", new Object(), 1L)).doesNotThrowAnyException();
        assertThat(EtagContext.currentGeneration("demoTasks/101")).isEqualTo(2L);
    }

    @Test
    void expectation_notConsumed_staleLoadAsserts409() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 1L);
        assertThatThrownBy(() -> EtagContext.recordLoaded("demoTasks/101", new Object(), 1L))
                .isInstanceOf(com.airedgesoft.lib.exception.DomainException.class);
    }

    @Test
    void withExpected_resetsConsumption_perScope() {
        requestScope();
        EtagContext.withExpected("demoTasks/101", 0L, () -> {
            EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
            EtagContext.recordSaved("demoTasks/101", new Object(), 1L);
        });
        // 同线程复用场景：新作用域 = 新期望，消费标记不残留（否则重放冲突断言被静默跳过）
        EtagContext.withExpected("demoTasks/101", 0L,
                () -> assertThatThrownBy(() -> EtagContext.recordLoaded("demoTasks/101", new Object(), 1L))
                        .isInstanceOf(com.airedgesoft.lib.exception.DomainException.class));
    }

    // ── 自动观测（只观测不断言）与跨集合隔离 ──

    @Test
    void recordObserved_neverAssertsExpectation() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 1L);
        // 自动观测路径收到其他表 / 陈旧版本行也不触发断言（跨表同值主键防误 409）
        assertThatCode(() -> EtagContext.recordObserved("101", new Object(), 99L)).doesNotThrowAnyException();
        assertThat(EtagContext.currentStorageVersion("101")).isEqualTo(99L);
    }

    @Test
    void keys_areExactMatch_noSuffixFallback() {
        requestScope();
        EtagContext.bindLockedVersion("boms/123", 5L);
        // 后缀模糊回退已禁止：不存在的键精确落空
        assertThat(EtagContext.expectedStorageVersion("boms/124")).isNull();
        assertThat(EtagContext.expectedStorageVersion("124")).isNull();
        // 相对名与裸 id 成对双槽（仓储 / 回调只有裸 id 的精确匹配桥；跨集合同号由 §2 雪花 ID 全局唯一兜底）
        assertThat(EtagContext.expectedStorageVersion("boms/123")).isEqualTo(4L);
        assertThat(EtagContext.expectedStorageVersion("123")).isEqualTo(4L);
        // 未登记资源的同号 id：无当前版本（不注入 lockedVersion）
        assertThat(EtagContext.currentGeneration("items/123")).isNull();
    }

    @Test
    void recordLoaded_softDeletedInstance_skipsAssertion() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 1L);
        // 软删行（getDeleteTime 非空）加载不断言：回收站单体 GET 照常 200（§5.1）
        Object deleted = new SoftDeletedStub();
        assertThatCode(() -> EtagContext.recordLoaded("demoTasks/101", deleted, 7L)).doesNotThrowAnyException();
        assertThat(EtagContext.currentStorageVersion("demoTasks/101")).isEqualTo(7L);
    }

    /** 软删形态样例（getDeleteTime 非空 = 已删）。 */
    static class SoftDeletedStub {
        public java.time.Instant getDeleteTime() {
            return java.time.Instant.EPOCH;
        }
    }

    // ── 409 文案格式化（C1 回归：实参槽位与模板占位符必须对齐）──

    @Test
    void versionConflictMessage_rendersAllPlaceholders_noRawPercentS() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 1L);
        com.airedgesoft.lib.exception.DomainException e = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> EtagContext.recordLoaded("demoTasks/101", new Object(), 5L),
                com.airedgesoft.lib.exception.DomainException.class);
        assertThat(e).isNotNull();
        assertThat(e.getMessage()).contains("demoTasks/101").doesNotContain("%s");
    }

    @Test
    void versionConflictMessage_twoArgShape_matchesTemplate() {
        // 统一构造形态：2 个实参（资源 id、期望版本）——与模板 2 个占位符对齐
        com.airedgesoft.lib.exception.DomainException e = new com.airedgesoft.lib.exception.DomainException(
                com.airedgesoft.lib.restful.DomainRestCode.VERSION_CONFLICT, "demoTasks/101", 0L);
        assertThat(e.getMessage()).isEqualTo("资源 demoTasks/101 已变化（期望版本 0），请刷新后重试");
    }

    // ── 观测与代数出口 ──

    @Test
    void recordLoadedThenSaved_exposesGeneration() {
        requestScope();
        Object instance = new Object();
        EtagContext.recordLoaded("demoTasks/101", instance, 4L);
        assertThat(EtagContext.currentGeneration("demoTasks/101")).isEqualTo(5L); // storage + 1
        assertThat(EtagContext.currentStorageVersion("demoTasks/101")).isEqualTo(4L);
        EtagContext.recordSaved("demoTasks/101", instance, 5L);
        assertThat(EtagContext.currentGeneration("demoTasks/101")).isEqualTo(6L);
    }

    @Test
    void recordLoaded_nullVersion_fallsBackToGenerationOne() {
        requestScope();
        EtagContext.recordLoaded("demoTasks/101", new Object(), null);
        assertThat(EtagContext.currentGeneration("demoTasks/101")).isEqualTo(1L);
    }

    @Test
    void unknownResource_generationIsNull() {
        requestScope();
        assertThat(EtagContext.currentGeneration("demoTasks/404")).isNull();
    }

    // ── 编程式作用域（非 HTTP 线程 / 单测） ──

    @Test
    void withExpected_scopesAndRestores() {
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isNull(); // 无请求上下文不抛
        Object instance = new Object();
        EtagContext.withExpected("demoTasks/101", 2L, () -> {
            assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isEqualTo(2L);
            EtagContext.recordLoaded("demoTasks/101", instance, 2L);
            assertThat(EtagContext.currentGeneration("demoTasks/101")).isEqualTo(3L);
        });
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isNull();
        assertThat(EtagContext.currentGeneration("demoTasks/101")).isNull();
    }

    @Test
    void withExpected_nestedActionFailure_stillRestores() {
        assertThatCode(() -> EtagContext.withExpected("demoTasks/101", 2L, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isNull();
    }

    // ── 生命周期 ──

    @Test
    void requestScopeIsolation_twoRequestsDoNotLeak() {
        requestScope();
        EtagContext.bindLockedVersion("demoTasks/101", 3L);
        RequestContextHolder.resetRequestAttributes(); // 请求结束
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isNull();
    }

    private static void requestScope() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }
}
