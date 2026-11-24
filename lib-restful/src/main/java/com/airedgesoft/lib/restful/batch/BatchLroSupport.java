package com.airedgesoft.lib.restful.batch;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.lro.Operation;
import com.airedgesoft.lib.restful.lro.OperationStatus;
import com.airedgesoft.lib.restful.lro.OperationStore;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量写<b>异步通道</b>（{@code @LroOperation}，缺省形态）的编排支撑（v4-restful-api.md §4.1；Tier B「声明式接管」）。
 *
 * <p>
 * 承担 §4.1 异步通道的三件框架事务，宿主（demo / 各 BC）触发端点与 Worker 只做装配：
 *
 * <ul>
 * <li><b>逐项独立执行 + 失败记账</b>：{@link #run}——逐条目调用单项执行单元（事务边界由应用层命令服务的单条用例自带，One Transaction Rule），单项
 * {@link DomainException} 折算 {@link Operation.OperationMetadata.FailedRequest} 记入失败账目、<b>不中断批次</b>（其余条目继续）；</li>
 * <li><b>终态装配</b>：{@link #finalize}——全部失败置 {@code Operation.error}（{@link DomainRestCode#BATCH_ALL_FAILED}，对齐
 * AIP-235「全败 Aborted」）；部分 / 全部成功置 {@code response} = <b>仅成功项 id + 计数</b>（不带完整资源表示，明细由调用方按需 {@code :batchGet} 补拉）；失败账目恒落
 * {@code metadata.batch}（{@code failedRequests}）；</li>
 * <li><b>终态争夺仲裁</b>：{@link #runAndFinalize}——终态经 {@link OperationStore#compareAndStatus}（RUNNING→终态）CAS 落库，与并发
 * {@code :cancel} 原子仲裁，绝不以裸 {@code save} 覆写已确认的取消终态（{@link OperationStore} 契约）。</li>
 * </ul>
 *
 * <p>
 * <b>失败账目语义（D19）</b>：条目 {@code {index, code, description, field?}} 与同步侧 {@code fieldViolations} 条目同源（ResCode 码值 /
 * 格式化文案；{@code field} 取首条字段级违规），语义与单条端点错误完全一致——白名单外意外异常<b>不逐项记账</b>，由 {@link #runAndFinalize} 单点收口为整操作失败终态（细节只进服务端日志，
 * 不透传调用方，exception-handling §5）。
 *
 * <p>
 * <b>协议类型归属</b>：本支撑只产出 Operation 协议载荷（lib 内部 + adapters 消费），application 层 Worker 签名保持纯数据。
 */
public final class BatchLroSupport {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchLroSupport.class);

    private BatchLroSupport() {
    }

    /**
     * 单项执行单元：返回资源 id（成功）；返回 {@code null} 表示稀疏跳过（如不存在/已删，不计入成功也不计入失败）；抛 {@link DomainException}
     * 记入失败账目、不中断批次。事务边界由单条用例自带。
     */
    @FunctionalInterface
    public interface ItemCall {

        /** 执行第 {@code index} 条目（0-based，对齐请求条目下标）。返回 {@code null} 表示稀疏跳过。 */
        String execute(int index);
    }

    /** 执行账目：成功项 id（保序）+ 失败账目（下标对齐请求条目）。 */
    public record BatchOutcome(List<String> succeededIds,
            List<Operation.OperationMetadata.FailedRequest> failedRequests) {

        public BatchOutcome {
            succeededIds = List.copyOf(succeededIds);
            failedRequests = List.copyOf(failedRequests);
        }
    }

    /** 终态成功载荷（§4.1：仅成功项 id + 计数，不带完整资源表示）。 */
    public record BatchResult(List<String> succeededIds, int succeededCount, int failedCount) {
    }

    /**
     * 逐项独立执行 + 失败记账。
     *
     * <p>
     * 不整批回滚：单项失败（含执行期版本冲突 409 级 {@link DomainException}）记入失败账目、其余条目继续（§4.1 异步通道）。
     *
     * <p>
     * <b>catch 白名单</b>（exception-handling §7.1）：per-item 仅捕获 {@link DomainException}（业务失败，语义与单条端点一致，账目即契约文本无需落日志）；
     * <b>白名单外异常（含基础设施不可用：连接池耗尽 / 超时 / 存储宕机）不 per-item 捕获</b>——原样上抛由 {@link #runAndFinalize} 收口为整操作失败终态， 防止基础设施故障被误记为单条
     * 500 记账、其余条目在故障设施上空转。
     *
     * @param expectedCount
     *            批次条目数（下标 0..expectedCount-1 对齐请求条目）
     * @param call
     *            单项执行单元
     */
    public static BatchOutcome run(int expectedCount, ItemCall call) {
        List<String> succeeded = new ArrayList<>(expectedCount);
        List<Operation.OperationMetadata.FailedRequest> failed = new ArrayList<>();
        for (int index = 0; index < expectedCount; index++) {
            try {
                String result = call.execute(index);
                if (result != null) {
                    succeeded.add(result);
                }
            } catch (DomainException e) {
                log.debug("异步批量第 {} 项业务失败（已记入失败账目）: {}", index, e.getMessage());
                failed.add(toFailedRequest(index, e));
            }
        }
        return new BatchOutcome(succeeded, failed);
    }

    /**
     * 终态装配（纯函数，不落 Store）：仅当无成功且存在失败项 → {@code Operation.error}（BATCH_ALL_FAILED，指引查看 {@code metadata.failedRequests}）；
     * 部分成功或全稀疏跳过 → {@code response} = 成功项 id + 计数，失败账目落 {@code metadata.batch}。
     */
    public static Operation finalize(Operation operation, BatchOutcome outcome) {
        int total = outcome.succeededIds().size() + outcome.failedRequests().size();
        Operation.OperationMetadata.BatchProgress batch = new Operation.OperationMetadata.BatchProgress(total,
                outcome.succeededIds().size(), outcome.failedRequests().size(), outcome.failedRequests());
        Operation withLedger = operation.withBatch(batch);
        if (!outcome.succeededIds().isEmpty() || outcome.failedRequests().isEmpty()) {
            return withLedger.withResult(new BatchResult(outcome.succeededIds(), outcome.succeededIds().size(),
                    outcome.failedRequests().size()));
        }
        DomainRestCode code = DomainRestCode.BATCH_ALL_FAILED;
        return withLedger.withError(new Operation.OperationError(code.getCode(), code.getDesc(), "BATCH_ALL_FAILED"));
    }

    /**
     * 提交阶段被执行器拒绝时将操作收敛为 503 UNAVAILABLE 失败终态（基础设施级终止沿用 HTTP 503/500 状态码，业务条目失败记 9 位 DomainRestCode），避免 pending
     * Operation 永挂。
     */
    public static Operation reject(OperationStore store, Operation operation) {
        log.warn("异步批量任务提交被拒绝: operationId={}, taskType={}", operation.operationId(), operation.taskType());
        Operation terminal = operation
                .withError(new Operation.OperationError(503, "batch execution rejected", "UNAVAILABLE"));
        return storeTerminal(store, operation, terminal);
    }

    /**
     * 编排入口（run + 终态装配 + CAS 仲裁）：执行批次并把终态经 {@link OperationStore#compareAndStatus} 落库（与并发 {@code :cancel} 原子仲裁， 取消已确认则
     * CAS 落空、回读取消终态返回）。
     *
     * <p>
     * <b>意外异常收口</b>（白名单外异常的唯一捕获点，单点记录）：{@code run} 逐项执行期间上抛的 {@link RuntimeException}（基础设施不可用等）在此 <b>一次性</b>
     * log.warn（含堆栈，细节只进服务端日志，exception-handling §5）并置整操作失败终态（固定文案，不透传内部异常）—— 对齐 demo {@code :import} Worker
     * 先例；已提交条目不回滚（异步通道语义，逐项账目以落库事实为准）。
     *
     * @param operation
     *            已落 Store 的进行中 Operation（{@code Operation.pending} + {@code store.save} 之后传入）
     */
    public static Operation runAndFinalize(OperationStore store, Operation operation, int expectedCount,
            ItemCall call) {
        BatchOutcome outcome;
        try {
            outcome = run(expectedCount, call);
        } catch (RuntimeException e) {
            log.warn("异步批量执行失败（整操作失败终态）: operationId={}, taskType={}", operation.operationId(), operation.taskType(),
                    e);
            return storeTerminal(store, operation,
                    operation.withError(new Operation.OperationError(500, "batch execution failed", "INTERNAL")));
        }
        return finalizeAndStore(store, operation, outcome);
    }

    /**
     * 终态装配 + CAS 仲裁（不执行批次——账目已由宿主产出，如整批服务返回结果折算）；与并发 {@code :cancel} 原子仲裁。
     */
    public static Operation finalizeAndStore(OperationStore store, Operation operation, BatchOutcome outcome) {
        return storeTerminal(store, operation, finalize(operation, outcome));
    }

    /** 终态经 CAS（RUNNING→终态）落库，与并发 {@code :cancel} 原子仲裁；取消已确认则 CAS 落空、回读取消终态返回。 */
    private static Operation storeTerminal(OperationStore store, Operation operation, Operation terminal) {
        return store.compareAndStatus(operation.operationId(), OperationStatus.RUNNING, op -> terminal)
                .orElseGet(() -> store.find(operation.operationId()).orElse(terminal));
    }

    /** {@link DomainException} → 失败账目条目（code / description 与 fieldViolations 条目同源；field 取首条字段级违规，无可定位字段时为 null）。 */
    private static Operation.OperationMetadata.FailedRequest toFailedRequest(int index, DomainException e) {
        String field = e.getFieldViolations().isEmpty() ? null : e.getFieldViolations().get(0).field();
        return new Operation.OperationMetadata.FailedRequest(index, e.getResCode().getCode(), e.getMessage(), field);
    }
}
