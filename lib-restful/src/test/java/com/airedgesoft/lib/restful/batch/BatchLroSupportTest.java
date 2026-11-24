package com.airedgesoft.lib.restful.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.batch.BatchLroSupport.BatchOutcome;
import com.airedgesoft.lib.restful.lro.InMemoryOperationStore;
import com.airedgesoft.lib.restful.lro.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 异步批量编排支撑的单测（§4.1 异步通道）：失败记账、全败 error 终态、白名单外异常整操作收口（exception-handling §7.1）。
 */
class BatchLroSupportTest {

    private final InMemoryOperationStore store = new InMemoryOperationStore();

    @Test
    @DisplayName("部分成功：DomainException 逐项记账，其余条目继续，response = id + 计数")
    void testRun_domainExceptionLedgered_batchContinues() {
        BatchOutcome outcome = BatchLroSupport.run(3, index -> {
            if (index == 1) {
                throw new DomainException(DomainErrorType.NOT_FOUND, DomainRestCode.OPERATION_NOT_FOUND, "x");
            }
            return "id-" + index;
        });
        assertThat(outcome.succeededIds()).containsExactly("id-0", "id-2");
        assertThat(outcome.failedRequests()).hasSize(1);
        assertThat(outcome.failedRequests().get(0).index()).isEqualTo(1);
        assertThat(outcome.failedRequests().get(0).code()).isEqualTo(DomainRestCode.OPERATION_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("全败：finalize 置 Operation.error（BATCH_ALL_FAILED），账目仍在 metadata.batch")
    void testFinalize_allFailed_errorTerminalWithLedger() {
        Operation operation = Operation.pending(Operation.newId(), "batch");
        BatchOutcome allFailed = BatchLroSupport.run(1, index -> {
            throw new DomainException(DomainErrorType.CONFLICT, DomainRestCode.VERSION_CONFLICT);
        });
        Operation terminal = BatchLroSupport.finalize(operation, allFailed);
        assertThat(terminal.done()).isTrue();
        assertThat(terminal.error().code()).isEqualTo(DomainRestCode.BATCH_ALL_FAILED.getCode());
        assertThat(terminal.metadata().batch().failedRequests()).hasSize(1);
        assertThat(terminal.metadata().batch().succeededCount()).isZero();
    }

    @Test
    @DisplayName("白名单外异常不逐项记账：上抛至编排收口为整操作 error 终态（已提交项不回滚）")
    void testRunAndFinalize_unexpectedException_wholeOperationError() {
        Operation operation = Operation.pending(Operation.newId(), "batch");
        store.save(operation);
        Operation result = BatchLroSupport.runAndFinalize(store, operation, 2, index -> {
            if (index == 1) {
                // 基础设施类意外异常：不得被 per-item 吞掉记成单条 500（§7.1 白名单）
                throw new IllegalStateException("connection pool exhausted");
            }
            return "id-0";
        });
        assertThat(result.done()).isTrue();
        assertThat(result.error().code()).isEqualTo(500);
        assertThat(result.response()).isNull();
        assertThat(result.metadata().batch()).as("意外异常不产生逐项账目").isNull();
    }

    @Test
    @DisplayName("部分成功：finalize 置 response = id + 计数，账目落 metadata.batch")
    void testFinalize_partialSuccess_responseWithIdsAndCounts() {
        Operation operation = Operation.pending(Operation.newId(), "batch");
        BatchOutcome outcome = new BatchOutcome(java.util.List.of("a", "b"),
                java.util.List.of(new Operation.OperationMetadata.FailedRequest(2, 300300002, "标题重复", null)));
        Operation terminal = BatchLroSupport.finalize(operation, outcome);
        assertThat(terminal.done()).isTrue();
        assertThat(terminal.error()).isNull();
        BatchLroSupport.BatchResult response = (BatchLroSupport.BatchResult) terminal.response();
        assertThat(response.succeededIds()).containsExactly("a", "b");
        assertThat(response.succeededCount()).isEqualTo(2);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(terminal.metadata().batch().failedRequests()).hasSize(1);
    }

    @Test
    @DisplayName("稀疏跳过：ItemCall 返回 null 时不计入成功也不计入失败，全跳过仍正常收口")
    void testRun_nullSparseSkip_omittedFromLedgerAndAllSkippedSucceeds() {
        Operation operation = Operation.pending(Operation.newId(), "batchDelete");
        store.save(operation);
        Operation mixed = BatchLroSupport.runAndFinalize(store, operation, 2, index -> index == 0 ? null : "id-1");
        assertThat(mixed.done()).isTrue();
        assertThat(mixed.error()).isNull();
        assertThat(mixed.metadata().batch().processedCount()).isEqualTo(1);
        assertThat(mixed.metadata().batch().succeededCount()).isEqualTo(1);

        Operation allSkippedOp = Operation.pending(Operation.newId(), "batchDelete");
        store.save(allSkippedOp);
        Operation allSkipped = BatchLroSupport.runAndFinalize(store, allSkippedOp, 2, index -> null);
        assertThat(allSkipped.done()).isTrue();
        assertThat(allSkipped.error()).isNull();
        assertThat(allSkipped.metadata().batch().processedCount()).isZero();
    }

    @Test
    @DisplayName("执行器拒绝：reject 将 pending 操作推进为 503 UNAVAILABLE 终态")
    void testReject_marksUnavailableTerminalError() {
        Operation operation = Operation.pending(Operation.newId(), "batch");
        store.save(operation);
        Operation rejected = BatchLroSupport.reject(store, operation);
        assertThat(rejected.done()).isTrue();
        assertThat(rejected.error().code()).isEqualTo(503);
        assertThat(rejected.error().reason()).isEqualTo("UNAVAILABLE");
    }
}
