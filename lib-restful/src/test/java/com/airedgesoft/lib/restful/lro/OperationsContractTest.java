package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.pagination.PageSizeResolver;
import com.airedgesoft.lib.restful.pagination.PageTokens;
import com.airedgesoft.lib.restful.pagination.PaginationProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperationsController 与 LroOperationAspect 单测（组件 7；Mock OperationStore / Response，不起容器）。 错误路径断言抛出的
 * {@link DomainException}（HTTP 渲染由 {@link LroProblemAdvice} 承担，见 {@link LroProblemAdviceTest}）。
 */
class OperationsContractTest {

    /** 游标编解码器（测试专用密钥；TTL 缺省足够覆盖单测时长）。 */
    private static final PageTokens PAGE_TOKENS = new PageTokens("shared-kernel-test-secret", Duration.ofMinutes(5));

    private final InMemoryOperationStore store = new InMemoryOperationStore();

    private final PageSizeResolver pageSizeResolver = new PageSizeResolver(new PaginationProperties(), null);

    private final OperationsController controller = new OperationsController(store, null, PAGE_TOKENS,
            pageSizeResolver);

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 轮询端点 ──

    @DisplayName("轮询端点：operation 存在时返回该 Operation 记录")
    @Test
    void get_found_returnsOperation() {
        store.save(Operation.pending("101", "copy"));
        Operation operation = controller.get("101");
        assertThat(operation.operationId()).isEqualTo("101");
    }

    @DisplayName("轮询端点：operation 不存在抛 404 OPERATION_NOT_FOUND")
    @Test
    void get_missing_throwsNotFound404() {
        assertThatThrownBy(() -> controller.get("999999")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.OPERATION_NOT_FOUND);
            assertThat(domainError.getType().httpStatus()).isEqualTo(404);
        });
    }

    // ── 列表端点（AIP-158 游标分页；createTime desc + operationId asc 平局仲裁） ──

    @DisplayName("列表端点：首页按 createTime 降序、operationId 升序平局仲裁，一页装下不返回 nextToken")
    @Test
    void list_firstPage_ordersCreateTimeDescThenOperationIdAsc_withoutNextToken() {
        store.save(op("201", "2026-03-15T09:30:00Z"));
        store.save(op("202", "2026-03-15T09:30:00Z")); // 与 201 同刻：主键升序兜底
        store.save(op("203", "2026-03-15T10:00:00Z"));
        store.save(op("204", "2026-03-15T08:00:00Z"));

        Page<Operation> page = controller.list(null, null);
        assertThat(page.items()).extracting(Operation::operationId).containsExactly("203", "201", "202", "204");
        assertThat(page.nextPageToken()).isNull(); // 默认 pageSize 20 一页装下
        assertThat(page.totalSize()).isEqualTo(4);
    }

    @DisplayName("列表端点：游标翻页按全序遍历无重复无遗漏，毫秒精度差异按时刻而非字符串排序")
    @Test
    void list_pagination_walksAllPagesInOrderWithoutDuplicationOrLoss() {
        store.save(op("511", "2026-03-15T11:00:00Z"));
        store.save(op("512", "2026-03-15T10:00:00Z"));
        store.save(op("513", "2026-03-15T10:00:00Z")); // 同刻平局
        store.save(op("514", "2026-03-15T10:00:00.100Z")); // 毫秒精度差异：按时刻排序而非字符串序
        store.save(op("515", "2026-03-15T09:00:00Z"));

        Set<String> seen = new LinkedHashSet<>();
        String token = null;
        int pages = 0;
        do {
            Page<Operation> page = controller.list(2, token);
            page.items().forEach(operation -> seen.add(operation.operationId()));
            token = page.nextPageToken();
            pages++;
        } while (token != null && pages < 10);
        assertThat(pages).isEqualTo(3); // pageSize=2 × 5 条 = 3 页
        // 期望序：11:00 → 10:00.100（10:00 之后）→ 10:00 同刻主键升序 → 更早
        assertThat(seen).containsExactly("511", "514", "512", "513", "515");
    }

    @DisplayName("列表端点：游标项在翻页间隙被删除，复合排序键 seek 仍正确续读后续数据")
    @Test
    void list_cursorItemDeletedBeforeNextPage_seekStillPositionsCorrectly() {
        store.save(op("601", "2026-03-15T11:00:00Z"));
        store.save(op("602", "2026-03-15T10:00:00Z"));
        store.save(op("603", "2026-03-15T09:00:00Z"));

        Page<Operation> first = controller.list(2, null);
        assertThat(first.items()).extracting(Operation::operationId).containsExactly("601", "602");
        // 游标项（602）在翻页间隙被删除：复合排序键 seek 不依赖游标行存在
        store.delete("602");
        Page<Operation> second = controller.list(2, first.nextPageToken());
        assertThat(second.items()).extracting(Operation::operationId).containsExactly("603");
        assertThat(second.nextPageToken()).isNull();
    }

    @DisplayName("列表端点：非法 pageToken 抛 400 INVALID_ARGUMENT")
    @Test
    void list_invalidPageToken_throwsInvalid400() {
        assertThatThrownBy(() -> controller.list(2, "not-a-valid-token")).isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).getType().httpStatus()).isEqualTo(400));
    }

    @DisplayName("列表端点：篡改 pageToken 经 GCM 认证失败抛 400")
    @Test
    void list_tamperedPageToken_throwsInvalid400() {
        store.save(op("801", "2026-03-15T11:00:00Z"));
        store.save(op("802", "2026-03-15T10:00:00Z"));
        store.save(op("803", "2026-03-15T09:00:00Z"));
        Page<Operation> first = controller.list(2, null);
        String token = first.nextPageToken();
        assertThat(token).isNotNull();
        // 翻转中段字符：末位字符可能仅承载 Base64 填充位，翻转后解码字节等价（GCM 认证仍通过 → 用例假绿）
        int index = token.length() / 2;
        char flipped = token.charAt(index) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, index) + flipped + token.substring(index + 1);

        // GCM 认证失败 → 400
        assertThatThrownBy(() -> controller.list(2, tampered)).isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).getType().httpStatus()).isEqualTo(400));
    }

    @DisplayName("列表端点：pageSize 负数抛 400 PAGE_SIZE_INVALID；0 取默认不报错（AIP-158:63-64/68）")
    @Test
    void list_negativePageSize_throwsInvalid400_zeroUsesDefault() {
        assertThatThrownBy(() -> controller.list(-1, null)).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getType().httpStatus()).isEqualTo(400);
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.PAGE_SIZE_INVALID);
        });
        // pageSize=0 → 取默认（AIP-158:63-64），返回 Page 信封而非 400
        assertThat(controller.list(0, null)).isNotNull();
    }

    // ── 路径变量形态校验（§2 资源 ID 规范形态）：非数字 / 前导零 → 400，不落入 Store 查询 ──

    @DisplayName("轮询端点：operationId 非规范形态（非数字）→ 400 RESOURCE_ID_INVALID")
    @Test
    void get_nonCanonicalId_throws400ResourceIdInvalid() {
        assertThatThrownBy(() -> controller.get("abc")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getType().httpStatus()).isEqualTo(400);
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.RESOURCE_ID_INVALID);
        });
    }

    @DisplayName("取消端点：operationId 前导零（非规范形态）→ 400 RESOURCE_ID_INVALID")
    @Test
    void cancel_leadingZeroId_throws400ResourceIdInvalid() {
        assertThatThrownBy(() -> controller.cancel("0101")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getType().httpStatus()).isEqualTo(400);
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.RESOURCE_ID_INVALID);
        });
    }

    @DisplayName("删除端点：operationId 非规范形态 → 400 RESOURCE_ID_INVALID，Store 不被触碰")
    @Test
    void delete_nonCanonicalId_throws400ResourceIdInvalid() {
        store.save(op("101", "2026-03-15T11:00:00Z"));
        assertThatThrownBy(() -> controller.delete("1x01")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getType().httpStatus()).isEqualTo(400);
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.RESOURCE_ID_INVALID);
        });
        assertThat(store.find("101")).isPresent(); // 形态校验先于业务语义，记录不受影响
    }

    @DisplayName("列表端点：超上限 pageSize 钳到配置最大值而非 400")
    @Test
    void list_oversizePageSize_clampedToConfiguredMax() {
        store.save(op("701", "2026-03-15T09:30:00Z"));
        store.save(op("702", "2026-03-15T09:31:00Z"));

        Page<Operation> page = controller.list(10_000, null); // 超上限钳到 100，不 400
        assertThat(page.items()).hasSize(2);
        assertThat(page.nextPageToken()).isNull();
    }

    // ── 删除端点（AIP-151 deleteOperation：终态可删 204；进行中 409；不存在 404） ──

    @DisplayName("删除端点：已成功终态可删（void + @ResponseStatus 204 由 MVC 层装配；直调验证业务语义），从 Store 移除")
    @Test
    void delete_succeededOperation_returnsVoid204AndRemovesFromStore() {
        store.save(Operation.pending("101", "copy").withResult("ok"));
        assertThatCode(() -> controller.delete("101")).doesNotThrowAnyException(); // void + @ResponseStatus(204)
        assertThat(store.find("101")).isEmpty();
    }

    @DisplayName("删除端点：已取消终态可删（204 由 MVC 层装配；直调验证业务语义），从 Store 移除")
    @Test
    void delete_cancelledOperation_returnsVoid204() {
        store.save(Operation.pending("101", "copy").withError(Operation.cancelled()));
        assertThatCode(() -> controller.delete("101")).doesNotThrowAnyException();
        assertThat(store.find("101")).isEmpty();
    }

    @DisplayName("删除端点：进行中 operation 抛 409 OPERATION_NOT_DELETABLE 且记录保留")
    @Test
    void delete_runningOperation_throwsConflict409AndKeepsOperation() {
        store.save(Operation.pending("101", "copy"));
        assertThatThrownBy(() -> controller.delete("101")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.OPERATION_NOT_DELETABLE);
            assertThat(domainError.getType().httpStatus()).isEqualTo(409);
        });
        assertThat(store.find("101")).isPresent(); // 进行中不可删，记录保留
    }

    @DisplayName("删除端点：operation 不存在抛 404")
    @Test
    void delete_missingOperation_throwsNotFound404() {
        assertThatThrownBy(() -> controller.delete("999999")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.OPERATION_NOT_FOUND);
            assertThat(domainError.getType().httpStatus()).isEqualTo(404);
        });
    }

    // ── 等待端点（AIP-151 WaitOperation：长轮询至终态或超时返回快照） ──

    @DisplayName("等待端点：已终态 operation 立即返回该终态")
    @Test
    void wait_doneOperation_returnsTerminalImmediately() {
        store.save(Operation.pending("101", "copy").withResult("ok"));
        Operation result = controller.await("101", 5);
        assertThat(result.done()).isTrue();
        assertThat(result.result()).isEqualTo("ok");
    }

    @DisplayName("等待端点：进行中 operation 超时（timeout=0）返回当前快照，仍 done=false 不报错")
    @Test
    void wait_runningOperation_timeoutReturnsSnapshot() {
        store.save(Operation.pending("101", "copy"));
        Operation result = controller.await("101", 0); // 0 预算 → 立即返回快照
        assertThat(result.done()).isFalse();
        assertThat(result.operationId()).isEqualTo("101");
    }

    @DisplayName("等待端点：等待间隙 Worker 推进至终态，轮询观察到完成并返回终态")
    @Test
    void wait_workerCompletesDuringWait_observesTerminal() throws Exception {
        store.save(Operation.pending("101", "copy"));
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            store.save(Operation.pending("101", "copy").withResult("done"));
        });
        worker.start();
        Operation result = controller.await("101", 5); // 5s 预算；轮询应在 Worker 完成后返回终态
        worker.join();
        assertThat(result.done()).isTrue(); // 证明轮询循环重读 Store 并观察到 Worker 推进
        assertThat(result.result()).isEqualTo("done");
    }

    @DisplayName("等待端点：operation 不存在抛 404 OPERATION_NOT_FOUND")
    @Test
    void wait_missing_throwsNotFound404() {
        assertThatThrownBy(() -> controller.await("999999", 1)).isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).getResCode())
                        .isEqualTo(DomainRestCode.OPERATION_NOT_FOUND));
    }

    // ── 取消端点 ──

    @DisplayName("取消端点：pending operation 取消后置为 CANCELLED 终态并落 Store")
    @Test
    void cancel_pending_marksCancelledTerminal() {
        store.save(Operation.pending("101", "copy"));
        Operation body = controller.cancel("101");
        assertThat(body.done()).isTrue();
        assertThat(body.error().reason()).isEqualTo("CANCELLED");
        assertThat(store.find("101").get().done()).isTrue(); // 终态已落 Store
    }

    @DisplayName("取消端点：已完成 operation 再取消抛 409 OPERATION_NOT_CANCELLABLE")
    @Test
    void cancel_doneOperation_throwsConflict409() {
        store.save(Operation.pending("101", "copy").withResult("ok"));
        assertThatThrownBy(() -> controller.cancel("101")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.OPERATION_NOT_CANCELLABLE);
            assertThat(domainError.getType().httpStatus()).isEqualTo(409);
        });
    }

    @DisplayName("取消端点：重复取消幂等，返回原 CANCELLED 终态")
    @Test
    void cancel_repeatCancel_isIdempotent() {
        store.save(Operation.pending("101", "copy"));
        controller.cancel("101");
        Operation repeated = controller.cancel("101");
        assertThat(repeated.error().reason()).isEqualTo("CANCELLED"); // 幂等取消返回原终态
    }

    @DisplayName("取消端点：operation 不存在抛 404")
    @Test
    void cancel_missing_throwsNotFound404() {
        assertThatThrownBy(() -> controller.cancel("999999")).isInstanceOf(DomainException.class).satisfies(error -> {
            DomainException domainError = (DomainException) error;
            assertThat(domainError.getResCode()).isEqualTo(DomainRestCode.OPERATION_NOT_FOUND);
            assertThat(domainError.getType().httpStatus()).isEqualTo(404);
        });
    }

    // ── CAS 取消语义（终态争夺原子仲裁，禁止 find-then-save 裸竞争） ──

    @DisplayName("CAS 取消：期望 RUNNING 匹配时应用取消并持久化终态到 Store")
    @Test
    void compareAndStatus_runningOperation_appliesAndPersists() {
        store.save(Operation.pending("101", "copy"));
        java.util.Optional<Operation> updated = store.compareAndStatus("101", OperationStatus.RUNNING,
                op -> op.withError(Operation.cancelled()));
        assertThat(updated).isPresent();
        assertThat(updated.get().error().reason()).isEqualTo("CANCELLED");
        assertThat(store.find("101").get().done()).isTrue(); // 推进已落 Store
    }

    @DisplayName("CAS 取消：状态已推进致期望落空，返回空且完成终态不被覆写")
    @Test
    void compareAndStatus_statusAlreadyAdvanced_expectedMiss_returnsEmptyWithoutWrite() {
        Operation completed = Operation.pending("101", "copy").withResult("ok");
        store.save(completed);
        assertThat(store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withError(Operation.cancelled())))
                .isEmpty(); // 先完成 → 取消侧 CAS 落空
        assertThat(store.find("101").get()).isEqualTo(completed); // 完成终态不被覆写
    }

    @DisplayName("CAS 取消：先取消后迟到完成侧 CAS 落空，取消终态存活不被 late-write 覆写")
    @Test
    void cancel_thenLateWorkerComplete_completionCasLoses_cancellationSurvives() {
        // 先 cancel 后 complete：完成侧（expected=RUNNING）CAS 落空，取消终态存活——last-write-wins 缺陷回归锚
        store.save(Operation.pending("101", "copy"));
        controller.cancel("101");
        assertThat(store.compareAndStatus("101", OperationStatus.RUNNING, op -> op.withResult("late-done"))).isEmpty();
        Operation stored = store.find("101").get();
        assertThat(stored.result()).isNull();
        assertThat(stored.error().reason()).isEqualTo("CANCELLED");
    }

    @DisplayName("CAS 取消：CAS 失败但已被并发方取消时，重读后按幂等取消返回 CANCELLED")
    @Test
    void cancel_casMissButConcurrentlyCancelledByOther_returnsIdempotentViaReread() {
        InMemoryOperationStore racing = new InMemoryOperationStore() {
            @Override
            public java.util.Optional<Operation> compareAndStatus(String operationId, OperationStatus expected,
                    java.util.function.UnaryOperator<Operation> updater) {
                // 模拟 CAS 失败瞬间：另一并发取消请求已抢先写入取消终态
                save(find(operationId).orElseThrow().withError(Operation.cancelled()));
                return java.util.Optional.empty();
            }
        };
        racing.save(Operation.pending("101", "copy"));
        Operation response = new OperationsController(racing, null, PAGE_TOKENS, pageSizeResolver).cancel("101");
        assertThat(response.error().reason()).isEqualTo("CANCELLED"); // 竞争败者按幂等取消处理
    }

    // ── 触发装配切面（202 + Location + Retry-After，统一 /v1/operations） ──

    @DisplayName("触发切面：返回 Operation 时装配 202 + 全局 Location 路径 + Retry-After")
    @Test
    void lroAspect_operationResult_assembles202WithGlobalLocation() throws Throwable {
        bindRequest();
        ProceedingJoinPoint pjp = joinPoint(Operation.pending("101", "copy"), "trigger");
        new LroOperationAspect(new LroProperties(), null, store).around(pjp);
        HttpServletResponse response = currentResponse();
        verify(response).setStatus(202);
        verify(response).setHeader("Location", "/v1/operations/101");
        verify(response).setIntHeader("Retry-After", 2);
    }

    @DisplayName("触发切面：owner 缺失时经 resolver 补填并回写 Store（「我的长运行任务列表」过滤源）")
    @Test
    void lroAspect_ownerMissing_fillsFromResolverAndWritesBackToStore() throws Throwable {
        bindRequest();
        Operation pending = Operation.pending("101", "copy");
        assertThat(pending.owner()).isNull(); // 触发端点方法体内保存的记录尚无 owner

        Operation assembled = (Operation) new LroOperationAspect(new LroProperties(), () -> "623780000000201", store)
                .around(joinPoint(pending, "trigger"));

        assertThat(assembled.owner()).isEqualTo("623780000000201"); // wire 返回值补填
        assertThat(store.find("101").orElseThrow().owner()).isEqualTo("623780000000201"); // Store 同步回写
    }

    @DisplayName("触发切面：owner 已登记或 resolver 未注册时不回写，Store 记录保持原样")
    @Test
    void lroAspect_ownerPresentOrResolverAbsent_storeUntouched() throws Throwable {
        bindRequest();
        // 触发端点方法体内已入库（owner 已登记）：切面不得覆盖
        store.save(Operation.pending("102", "copy").withOwner("623780000000999"));
        new LroOperationAspect(new LroProperties(), () -> "623780000000201", store)
                .around(joinPoint(store.find("102").orElseThrow(), "trigger"));
        assertThat(store.find("102").orElseThrow().owner()).isEqualTo("623780000000999"); // 已登记 → 不覆盖

        // resolver 未注册（开发 / 本地形态）：owner 留空不补填、不回写
        store.save(Operation.pending("103", "copy"));
        new LroOperationAspect(new LroProperties(), null, store)
                .around(joinPoint(store.find("103").orElseThrow(), "trigger"));
        assertThat(store.find("103").orElseThrow().owner()).isNull();
    }

    @DisplayName("触发切面：Store 已有 Worker 推进（owner 缺失）→ CAS 合并 owner，不覆写进度（#2 CAS 分支回归锚）")
    @Test
    void lroAspect_storedPresentOwnerMissing_casMergesOwnerWithoutOverwritingProgress() throws Throwable {
        bindRequest();
        // 触发端点已落库且 Worker 推进到 progress=40（owner 尚缺）——切面须 CAS 合并而非以触发快照整对象覆写
        store.save(Operation.pending("101", "copy").withProgress(40));
        Operation triggerSnapshot = Operation.pending("101", "copy"); // 陈旧触发快照 progress=0

        Operation assembled = (Operation) new LroOperationAspect(new LroProperties(), () -> "623780000000201", store)
                .around(joinPoint(triggerSnapshot, "trigger"));

        assertThat(assembled.owner()).isEqualTo("623780000000201");
        assertThat(assembled.progress()).isEqualTo(40); // 保留 Worker 进度，不被触发快照 progress=0 覆写
        Operation stored = store.find("101").orElseThrow();
        assertThat(stored.owner()).isEqualTo("623780000000201");
        assertThat(stored.progress()).isEqualTo(40); // Store 同步：进度存活 + owner 合并
    }

    @DisplayName("触发切面：Store 已终态（owner 缺失，demo :import 路径）→ CAS 合并 owner，终态与结果不被覆写")
    @Test
    void lroAspect_storedTerminalOwnerMissing_casMergesOwnerPreservingResult() throws Throwable {
        bindRequest();
        store.save(Operation.pending("101", "copy").withResult(java.util.Map.of("successCount", 3))); // done=true 终态
        Operation triggerSnapshot = Operation.pending("101", "copy");

        Operation assembled = (Operation) new LroOperationAspect(new LroProperties(), () -> "623780000000201", store)
                .around(joinPoint(triggerSnapshot, "trigger"));

        assertThat(assembled.owner()).isEqualTo("623780000000201");
        assertThat(assembled.done()).isTrue(); // 终态存活
        assertThat(assembled.result()).isEqualTo(java.util.Map.of("successCount", 3)); // 结果不被覆写
        assertThat(store.find("101").orElseThrow().owner()).isEqualTo("623780000000201");
    }

    @DisplayName("触发切面：注解 retryAfterSeconds 覆盖属性缺省值")
    @Test
    void lroAspect_annotationRetryAfterOverridesProperty() throws Throwable {
        bindRequest();
        ProceedingJoinPoint pjp = joinPoint(Operation.pending("101", "copy"), "triggerWithRetry");
        new LroOperationAspect(new LroProperties(), null, store).around(pjp);
        verify(currentResponse()).setIntHeader("Retry-After", 9);
    }

    @DisplayName("触发切面：非 Operation 返回值原样透传不装配 LRO 响应")
    @Test
    void lroAspect_nonOperationResult_passesThrough() throws Throwable {
        bindRequest();
        ProceedingJoinPoint pjp = joinPoint("同步结果", "trigger");
        assertThat(new LroOperationAspect(new LroProperties(), null, store).around(pjp)).isEqualTo("同步结果");
    }

    // ── 测试工具 ──

    /** 定时 Operation 构造：{@code pending} 走墙钟无法控制排序，测试经规范构造注入固定 createTime（RFC 3339）。 */
    private static Operation op(String operationId, String createTime) {
        return new Operation("operations/" + operationId,
                new Operation.OperationMetadata(operationId, "copy", 0, null, createTime, createTime, null), false,
                null, null);
    }

    @SuppressWarnings("unused")
    static class ContractHolder {

        @LroOperation(taskType = "copy")
        Object trigger() {
            return null;
        }

        @LroOperation(taskType = "copy", retryAfterSeconds = 9)
        Object triggerWithRetry() {
            return null;
        }
    }

    private static void bindRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // 长流程端点强制幂等键（§16）：切面在请求线程校验该头
        when(request.getHeader(com.airedgesoft.lib.restful.idempotency.IdempotencyFilter.KEY_HEADER))
                .thenReturn("test-key");
        RequestContextHolder
                .setRequestAttributes(new ServletRequestAttributes(request, mock(HttpServletResponse.class)));
    }

    @DisplayName("触发切面：缺 Idempotency-Key → 400 LRO_IDEMPOTENCY_KEY_REQUIRED（长流程必须要求幂等键，§16）")
    @Test
    void lroAspect_missingIdempotencyKey_throwsDomainException() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(mock(HttpServletRequest.class), mock(HttpServletResponse.class)));
        assertThatThrownBy(() -> new LroOperationAspect(new LroProperties(), () -> "owner", store)
                .around(joinPoint(Operation.pending("101", "copy"), "trigger")))
                .isInstanceOf(com.airedgesoft.lib.exception.DomainException.class)
                .satisfies(error -> assertThat(((com.airedgesoft.lib.exception.DomainException) error).getCode())
                        .isEqualTo(com.airedgesoft.lib.restful.DomainRestCode.LRO_IDEMPOTENCY_KEY_REQUIRED.getCode()));
    }

    private static HttpServletResponse currentResponse() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
    }

    private ProceedingJoinPoint joinPoint(Object result, String holderMethod) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(ContractHolder.class.getDeclaredMethod(holderMethod));
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }

    @DisplayName("OperationStore.delete 缺省实现：501 UNSUPPORTED_BY_STORE（未覆盖即显式暴露，不 500、不泄露实现类名）")
    @Test
    void operationStore_defaultDelete_throwsUnsupportedByStore() {
        com.airedgesoft.lib.restful.lro.OperationStore minimal = new com.airedgesoft.lib.restful.lro.OperationStore() {
            @Override
            public java.util.Optional<com.airedgesoft.lib.restful.lro.Operation> find(String operationId) {
                return java.util.Optional.empty();
            }

            @Override
            public void save(com.airedgesoft.lib.restful.lro.Operation operation) {
            }
        };
        assertThatThrownBy(() -> minimal.delete("1")).isInstanceOf(com.airedgesoft.lib.exception.DomainException.class)
                .satisfies(error -> {
                    com.airedgesoft.lib.exception.DomainException ex = (com.airedgesoft.lib.exception.DomainException) error;
                    assertThat(ex.getType()).isEqualTo(com.airedgesoft.lib.exception.DomainErrorType.UNIMPLEMENTED);
                    assertThat(ex.getCode())
                            .isEqualTo(com.airedgesoft.lib.restful.DomainRestCode.UNSUPPORTED_BY_STORE.getCode());
                    assertThat(ex.getMessage()).doesNotContain("OperationStore").doesNotContain("%s");
                });
    }
}
