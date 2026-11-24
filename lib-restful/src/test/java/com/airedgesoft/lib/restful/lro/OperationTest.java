package com.airedgesoft.lib.restful.lro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operation 状态对象与内存 Store 单测（组件 7）。
 */
class OperationTest {

    @Test
    void newId_pureNumericUniqueContractShape() {
        String first = Operation.newId();
        String second = Operation.newId();
        // lib-id 短雪花十进制串：纯数字、无前导零、long 范围（资源 ID 契约 §2），与全域资源 ID 同一 SSOT
        assertThat(first).matches("[1-9]\\d{5,18}");
        assertThat(Long.parseLong(first)).isPositive();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void lifecycle_progressThenTerminalStates() {
        Operation operation = Operation.pending("101", "demoTaskBatchCopy");
        assertThat(operation.done()).isFalse();
        assertThat(operation.progress()).isZero();

        Operation progressing = operation.withProgress(40);
        assertThat(progressing.done()).isFalse();
        assertThat(progressing.progress()).isEqualTo(40);

        Operation succeeded = progressing.withResult("batch-ok");
        assertThat(succeeded.done()).isTrue();
        assertThat(succeeded.progress()).isEqualTo(100);
        assertThat(succeeded.result()).isEqualTo("batch-ok");
        assertThat(succeeded.error()).isNull();

        Operation failed = operation.withError(Operation.cancelled());
        assertThat(failed.done()).isTrue();
        assertThat(failed.result()).isNull();
        assertThat(failed.error().reason()).isEqualTo("CANCELLED");

        assertThat(operation.updateTime()).isEqualTo(operation.createTime()); // 原实例不可变
    }

    @Test
    void inMemoryStore_saveFindRoundTrip() {
        InMemoryOperationStore store = new InMemoryOperationStore();
        assertThat(store.find("missing")).isEmpty();
        store.save(Operation.pending("101", "copy"));
        store.save(Operation.pending("101", "copy").withProgress(55));
        Optional<Operation> found = store.find("101");
        assertThat(found).isPresent();
        assertThat(found.get().progress()).isEqualTo(55); // 整体覆盖写（最后写赢）
    }

    // ── 多步骤复杂任务（AIP-151 metadata.steps；withStep 按 name upsert / 追加） ──

    @Test
    void multiStep_withStepUpsertsByNameThenAppends() {
        Operation operation = Operation.pending("101", "demoTaskImport")
                .withSteps(List.of(new Operation.OperationMetadata.Step("parse", "RUNNING", 0),
                        new Operation.OperationMetadata.Step("persist", "PENDING", 0)));
        assertThat(operation.metadata().steps()).hasSize(2);

        // 按 name upsert：parse 推进到 SUCCEEDED，条目数不变；persist 未触碰仍 PENDING
        Operation parseDone = operation.withStep("parse", "SUCCEEDED", 100);
        assertThat(parseDone.metadata().steps()).hasSize(2);
        assertThat(parseDone.metadata().steps().get(0))
                .isEqualTo(new Operation.OperationMetadata.Step("parse", "SUCCEEDED", 100));
        assertThat(parseDone.metadata().steps().get(1).status()).isEqualTo("PENDING");

        // name 不存在 → 追加新步骤
        Operation withNotify = parseDone.withStep("notify", "RUNNING", 10);
        assertThat(withNotify.metadata().steps()).hasSize(3);
        assertThat(withNotify.metadata().steps().get(2).name()).isEqualTo("notify");

        // 原实例不可变（withStep 返回新实例）
        assertThat(operation.metadata().steps().get(0).status()).isEqualTo("RUNNING");
    }

    @Test
    void multiStep_singleStepTaskHasNullStepsByDefault() {
        Operation operation = Operation.pending("101", "copy");
        assertThat(operation.metadata().steps()).isNull(); // 单步任务 steps 缺省
        // 首次 withStep 从空清单追加
        assertThat(operation.withStep("only", "RUNNING", 0).metadata().steps()).hasSize(1);
    }

    // ── wire 形态对齐 google.longrunning.Operation 骨架（兼容访问器 @JsonIgnore 不入 wire） ──

    @Test
    void wire_matchesLongRunningSkeleton_compatAccessorsNotSerialized() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Operation operation = Operation.pending("923809242866016", "demoTaskImport").withStep("parse", "SUCCEEDED", 100)
                .withProgress(50);
        JsonNode root = mapper.readTree(mapper.writeValueAsString(operation));

        // 顶层标准骨架键：name / metadata / done（error/response 为 null → NON_NULL 省略）
        List<String> fields = new ArrayList<>();
        root.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("name", "metadata", "done");
        assertThat(root.path("name").asText()).isEqualTo("operations/923809242866016");
        assertThat(root.path("done").asBoolean()).isFalse();

        // 兼容访问器不得泄漏到顶层 wire（operationId/taskType/progress/result 仅在 metadata 内）
        assertThat(root.has("operationId")).isFalse();
        assertThat(root.has("taskType")).isFalse();
        assertThat(root.has("progress")).isFalse();
        assertThat(root.has("result")).isFalse();

        // metadata 承载服务专属元数据 + 多步骤明细
        JsonNode metadata = root.path("metadata");
        assertThat(metadata.path("operationId").asText()).isEqualTo("923809242866016");
        assertThat(metadata.path("taskType").asText()).isEqualTo("demoTaskImport");
        assertThat(metadata.path("progress").asInt()).isEqualTo(50);
        assertThat(metadata.path("steps").get(0).path("name").asText()).isEqualTo("parse");
        assertThat(metadata.path("steps").get(0).path("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void wire_terminalSuccessCarriesResponseNotError() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Operation succeeded = Operation.pending("101", "copy").withResult(Map.of("ok", true));
        JsonNode root = mapper.readTree(mapper.writeValueAsString(succeeded));
        assertThat(root.path("done").asBoolean()).isTrue();
        assertThat(root.path("response").path("ok").asBoolean()).isTrue();
        assertThat(root.has("error")).isFalse(); // 成功 / 失败终态互斥
    }

    @Test
    void wire_terminalFailureCarriesErrorNotResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Operation failed = Operation.pending("101", "copy").withError(Operation.cancelled());
        JsonNode root = mapper.readTree(mapper.writeValueAsString(failed));
        assertThat(root.path("done").asBoolean()).isTrue();
        assertThat(root.path("error").path("reason").asText()).isEqualTo("CANCELLED");
        assertThat(root.has("response")).isFalse();
    }
}
