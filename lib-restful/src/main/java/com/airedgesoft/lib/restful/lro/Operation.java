package com.airedgesoft.lib.restful.lro;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * LRO（Long-Running Operation）状态对象（AIP-151；v4-restful-api.md §4 自定义方法异步形态，Tier B「声明式接管」）。
 *
 * <p>
 * <b>wire 形态对齐 {@code google.longrunning.Operation} 标准骨架</b>（AIP-151:41-42）：{@code {name, metadata, done, error,
 * response}}—— {@code name} 为服务端分配的资源名（{@code operations/{operationId}}）；{@code metadata} 承载服务专属元数据（任务类型 / 进度 / 多步骤 /
 * 时刻 / 发起人）； {@code done} 标记终态；终态二选一 {@code error}（失败，{@code google.rpc.Status} 形态）或 {@code response}（成功载荷）。
 *
 * <pre>
 * { "name": "operations/923809242866016",
 *   "metadata": { "operationId": "923809242866016", "taskType": "demoTaskImport", "progress": 40,
 *                 "steps": [ {"name": "parse", "status": "SUCCEEDED", "progress": 100},
 *                            {"name": "persist", "status": "RUNNING", "progress": 30} ],
 *                 "owner": "623780000000201", "createTime": "2026-03-15T09:30:00Z", "updateTime": "2026-03-15T09:30:02Z" },
 *   "done": false, "error": null, "response": null }
 * </pre>
 *
 * <p>
 * <b>多步骤复杂任务</b>：{@link OperationMetadata#steps()} 承载子步骤清单（{@link Step}：name / status / progress），Worker 经
 * {@link #withSteps} / {@link #withStep} 推进——单步任务 steps 缺省，多步任务逐步推进，整体 {@code progress} 与步骤明细并存。
 *
 * <p>
 * <b>协议类型归属</b>：本类型属 RESTful 协议载荷，仅在 lib-restful（本类 + Store + 端点）与 adapters（触发端点返回）流转；application 层 Worker
 * 签名保持纯数据。终态语义： {@code done=true} 且 {@code response} 与 {@code error} 互斥（成功 / 失败）；取消即失败终态（AIP-151
 * 未定义取消码：{@code done=true} + {@code error.reason="CANCELLED"}，本库提供 {@link #cancelled()} 工厂）。
 *
 * <p>
 * <b>兼容访问器</b>：{@link #operationId()} / {@link #taskType()} / {@link #progress()} / {@link #result()} 等委托 metadata /
 * response， 供 Java 消费方沿用（{@code @JsonIgnore}——不出现在 wire，wire 恒为标准骨架）。
 */
@JsonInclude(Include.NON_NULL)
public record Operation(String name, OperationMetadata metadata, boolean done, OperationError error, Object response) {

    /**
     * 契约校验（§2 资源 ID 规范形态）：{@code metadata.operationId} 必须为十进制纯数字、无前导零（单个 "0" 除外）且在 {@code long} 范围内—— 列表游标以
     * {@code Long} 装载主键兜底位，非规范形态在创建期快速失败。
     */
    public Operation {
        String operationId = metadata == null ? null : metadata.operationId();
        if (operationId == null || !operationId.matches("\\d+")
                || (operationId.length() > 1 && operationId.charAt(0) == '0')) {
            throw new IllegalArgumentException("operationId 必须为十进制规范形态数字（资源 ID 契约，§2）: " + operationId);
        }
        try {
            Long.parseLong(operationId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("operationId 超出 long 范围（资源 ID 契约，§2）: " + operationId, e);
        }
    }

    /**
     * 服务专属元数据（{@code google.longrunning.Operation.metadata} 的 Any 载荷；多步骤经 {@link #steps} 承载；批量类操作经 {@link #batch}
     * 承载失败账目）。
     */
    @JsonInclude(Include.NON_NULL)
    public record OperationMetadata(String operationId, String taskType, Integer progress, List<Step> steps,
            @Schema(type = "string", format = "date-time", accessMode = Schema.AccessMode.READ_ONLY, description = "操作创建时刻（RFC 3339）") String createTime,
            @Schema(type = "string", format = "date-time", accessMode = Schema.AccessMode.READ_ONLY, description = "操作最近更新时刻（RFC 3339）") String updateTime,
            @Schema(description = "发起人（「我的长运行任务列表」按此过滤；经 OperationOwnerResolver 装配，未配置时为空）") String owner,
            @Schema(description = "批量类操作的扩展进度与失败账目（§4.1 异步通道；非批量操作为 null 不入 wire）") BatchProgress batch) {

        /** 兼容构造（非批量操作；batch 置 null）。 */
        public OperationMetadata(String operationId, String taskType, Integer progress, List<Step> steps,
                String createTime, String updateTime, String owner) {
            this(operationId, taskType, progress, steps, createTime, updateTime, owner, null);
        }

        public OperationMetadata {
            steps = steps == null ? null : List.copyOf(steps);
        }

        /** 多步骤子任务明细（name 唯一标识；status = PENDING / RUNNING / SUCCEEDED / FAILED；progress 0–100 可选）。 */
        @JsonInclude(Include.NON_NULL)
        public record Step(String name, String status, Integer progress) {
        }

        /**
         * 批量类操作的扩展进度与失败账目（§4.1 异步通道；对齐 AIP-233/234/235 {@code failed_requests} 条款，载体形态本项目化——D19）。
         *
         * <p>
         * {@code failedRequests} 逐条记入单项失败（服务端重试可自愈的瞬时错误不记入）；全部失败时不逐项收尾，终态走 {@code Operation.error}（D19）。
         */
        @JsonInclude(Include.NON_NULL)
        public record BatchProgress(@Schema(description = "已处理条目数（成功 + 失败）", example = "42") int processedCount,
                @Schema(description = "成功条目数", example = "40") int succeededCount,
                @Schema(description = "失败条目数", example = "2") int failedCount,
                @Schema(description = "失败账目（下标对齐请求条目；语义与单条端点错误一致）") List<FailedRequest> failedRequests) {

            public BatchProgress {
                failedRequests = failedRequests == null ? List.of() : List.copyOf(failedRequests);
            }
        }

        /** 批量单项失败账目条目（{@code index} = 请求条目下标；与同步侧 fieldViolations 条目同源，D19）。 */
        @JsonInclude(Include.NON_NULL)
        public record FailedRequest(@Schema(description = "失败条目在请求中的下标（0-based）", example = "3") int index,
                @Schema(description = "业务错误码（与单条端点错误同码；全库唯一 ResCode 码值）", example = "300300002") int code,
                @Schema(description = "失败原因（ResCode 文案，与 fieldViolations.description 同源）") String description,
                @Schema(description = "可定位到具体字段时的字段路径（可选）", example = "title") String field) {
        }

        OperationMetadata withProgress(Integer newProgress) {
            return new OperationMetadata(operationId, taskType, newProgress, steps, createTime, now(), owner, batch);
        }

        OperationMetadata withSteps(List<Step> newSteps) {
            return new OperationMetadata(operationId, taskType, progress, newSteps, createTime, now(), owner, batch);
        }

        OperationMetadata withOwner(String newOwner) {
            return new OperationMetadata(operationId, taskType, progress, steps, createTime, updateTime, newOwner,
                    batch);
        }

        OperationMetadata withBatch(BatchProgress newBatch) {
            return new OperationMetadata(operationId, taskType, progress, steps, createTime, now(), owner, newBatch);
        }

        /** 仅推进 {@code updateTime}（终态装配不变更进度与步骤）。 */
        OperationMetadata touch() {
            return new OperationMetadata(operationId, taskType, progress, steps, createTime, now(), owner, batch);
        }
    }

    /** 失败明细（{@code google.rpc.Status} 形态：机器码 + 提示；{@code reason} 为 AirEdge 扩展判据，取消亦以此承载）。 */
    @JsonInclude(Include.NON_NULL)
    public record OperationError(int code, String message, String reason) {
    }

    /** 新建「进行中」Operation（progress 缺省 0；创建即落 Store）。 */
    public static Operation pending(String operationId, String taskType) {
        String now = now();
        return new Operation("operations/" + operationId,
                new OperationMetadata(operationId, taskType, 0, null, now, now, null), false, null, null);
    }

    /**
     * 全局 operationId 生成（统一走 lib-id 短雪花 {@link com.airedgesoft.lib.id.Ids}，与全域资源 ID 同一 SSOT； 纯数字字符串，契约 ID 规则同资源
     * §2）；触发端点创建 {@link #pending} 时取用。
     */
    public static String newId() {
        return com.airedgesoft.lib.id.Ids.newId();
    }

    /** 进度推进（0–100；越界值原样保留由 Worker 自律，不静默钳制——异常进度即异常信号）。 */
    public Operation withProgress(int progress) {
        return new Operation(name, metadata.withProgress(progress), done, error, response);
    }

    /** 多步骤清单整体设置（复杂任务初始化 / 批量推进）。 */
    public Operation withSteps(List<OperationMetadata.Step> steps) {
        return new Operation(name, metadata.withSteps(steps), done, error, response);
    }

    /** 单个子步骤推进（按 name upsert；不存在则追加）——多步骤复杂任务逐步推进入口。 */
    public Operation withStep(String stepName, String status, Integer progress) {
        List<OperationMetadata.Step> current = metadata.steps() == null ? List.of() : metadata.steps();
        List<OperationMetadata.Step> next = new ArrayList<>();
        boolean replaced = false;
        for (OperationMetadata.Step step : current) {
            if (step.name().equals(stepName)) {
                next.add(new OperationMetadata.Step(stepName, status, progress));
                replaced = true;
            } else {
                next.add(step);
            }
        }
        if (!replaced) {
            next.add(new OperationMetadata.Step(stepName, status, progress));
        }
        return withSteps(next);
    }

    /** 成功终态（progress 置 100；载荷落 {@code response}）。 */
    public Operation withResult(Object result) {
        return new Operation(name, metadata.withProgress(100), true, null, result);
    }

    /** 批量扩展进度 / 失败账目装配（§4.1 异步通道；与 {@link #withResult} / {@link #withError} 正交，两者均保留 batch）。 */
    public Operation withBatch(OperationMetadata.BatchProgress batch) {
        return new Operation(name, metadata.withBatch(batch), done, error, response);
    }

    /** 失败终态（含用户取消；载荷落 {@code error}，{@code response} 清空；仅推进 {@code updateTime}）。 */
    public Operation withError(OperationError error) {
        return new Operation(name, metadata.touch(), true, error, null);
    }

    /** 登记发起人（触发装配切面在 owner 缺失时补填；已登记则原样返回）。 */
    public Operation withOwner(String owner) {
        if (owner() != null || owner == null || owner.isBlank()) {
            return this;
        }
        return new Operation(name, metadata.withOwner(owner), done, error, response);
    }

    // ── 兼容访问器（委托 metadata / response；@JsonIgnore 不入 wire）──

    /** 操作 ID（{@code metadata.operationId}）。 */
    @JsonIgnore
    public String operationId() {
        return metadata == null ? null : metadata.operationId();
    }

    /** 任务类型（{@code metadata.taskType}）。 */
    @JsonIgnore
    public String taskType() {
        return metadata == null ? null : metadata.taskType();
    }

    /** 整体进度（{@code metadata.progress}）。 */
    @JsonIgnore
    public Integer progress() {
        return metadata == null ? null : metadata.progress();
    }

    /** 创建时刻（{@code metadata.createTime}）。 */
    @JsonIgnore
    public String createTime() {
        return metadata == null ? null : metadata.createTime();
    }

    /** 最近更新时刻（{@code metadata.updateTime}）。 */
    @JsonIgnore
    public String updateTime() {
        return metadata == null ? null : metadata.updateTime();
    }

    /** 发起人（{@code metadata.owner}）。 */
    @JsonIgnore
    public String owner() {
        return metadata == null ? null : metadata.owner();
    }

    /** 成功载荷别名（{@code response}；兼容旧 {@code result()} 消费方）。 */
    @JsonIgnore
    public Object result() {
        return response;
    }

    /** 当前时刻（RFC 3339 文本；毫秒截断统一精度，ISO_INSTANT 对整秒不输出小数位，存取两侧口径一致）。 */
    private static String now() {
        return java.time.format.DateTimeFormatter.ISO_INSTANT
                .format(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    /**
     * 取消终态工厂（{@code :cancel} 端点使用）。
     *
     * <p>
     * AIP-151 未定义取消专用错误码：机器判据取 {@code reason="CANCELLED"}（{@code done=true} + error 承载），{@code code} 置 0 表示未指定，不借用 HTTP
     * 499。
     */
    public static OperationError cancelled() {
        return new OperationError(0, "operation cancelled by client", "CANCELLED");
    }
}
