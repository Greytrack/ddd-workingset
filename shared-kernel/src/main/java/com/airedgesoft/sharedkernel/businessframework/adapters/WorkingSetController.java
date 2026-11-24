package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.EtagResource;
import com.airedgesoft.lib.restful.lro.LroOperation;
import com.airedgesoft.lib.restful.lro.OperationStore;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import com.airedgesoft.sharedkernel.businessframework.application.command.CreateWorkingsetCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.DeleteWorkingsetCommand;
import com.airedgesoft.sharedkernel.businessframework.application.query.GetWorkingsetQuery;
import com.airedgesoft.sharedkernel.businessframework.application.query.ListWorkingsetsQuery;
import com.airedgesoft.sharedkernel.businessframework.application.query.QWorkingset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作集资源入站适配器（顶栏工作集选择器 / 新建向导 / 编辑 / 添加 Tracker / 内容替换）。
 *
 * <p>
 * 向导树投影落在 {@link ProductSpaceController}；比较/合并已迁 trackers 族（{@link TrackerController}）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "共享内核-工作集", description = "工作集管理：列表/创建/编辑/删除/添加 Tracker/内容替换")
@RequestMapping("/v1/workingsets")
public class WorkingSetController {

    private final OperationStore operationStore;

    @Operation(operationId = "workingsetList", summary = "工作集列表（平铺集合，节点带 parentId+canView；空集合 200）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（平铺集合，§13 禁嵌套树 JSON；空集合 200）"),
            @ApiResponse(responseCode = "400", description = "分页参数非法（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping
    public Page<QWorkingset> list(
            @Parameter(description = "父工作集 id（可选；缺省全量平铺）") @RequestParam(value = "parentId", required = false) String parentId,
            @Parameter(description = "单页数量（缺省取系统配置；0 取默认；负数 → 400）", example = "20") @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @Parameter(description = "下一页游标（首页不传）") @RequestParam(value = "pageToken", required = false) String pageToken) {
        ListWorkingsetsQuery query = new ListWorkingsetsQuery(parentId, pageSize, pageToken);
        // mock: 待实现任务替换
        return Page.of(List.of(new QWorkingset("workingsets/623809242866016", "623809242866016", "示例工作集",
                "623780000000201", "赵拓", "门禁评审用工作集", "#3B82F6", null, true, "SHARED", "head",
                List.of("623780000000101"), "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z")), null, 1);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "workingsetCreate", summary = "创建工作集（201 + Location + ETag；重名 409；产品空间不存在 404；幂等键必接）")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "创建成功：Location 指向新工作集，ETag 为响应表示 hash（并发代数见 body lockedVersion）"),
            @ApiResponse(responseCode = "400", description = "参数 / 业务规则校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "产品空间不存在（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "工作集重名（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping
    @EtagResource
    public QWorkingset create(@RequestBody CreateWorkingsetCommand command) {
        // mock: 待实现任务替换
        return new QWorkingset("workingsets/623809242866016", "623809242866016", command.displayName(), command.owner(),
                null, command.description(), command.color(), null, true, command.branchType(), command.baselineRef(),
                command.productSpaces(), "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "workingsetGet", summary = "工作集详情（ETag；业务读恒 200；不存在 404；编辑表单即详情回显）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "成功，响应头携带 ETag"),
            @ApiResponse(responseCode = "404", description = "工作集不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping("/{id}")
    @EtagResource("id")
    public QWorkingset get(@PathVariable("id") String id) {
        GetWorkingsetQuery query = new GetWorkingsetQuery(id);
        // mock: 待实现任务替换
        return new QWorkingset("workingsets/" + id, id, "示例工作集", "623780000000201", "赵拓", "门禁评审用工作集", "#3B82F6", null,
                true, "SHARED", "head", List.of("623780000000101"), "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "workingsetUpdate", summary = "编辑工作集基本信息（掩码白名单 displayName/owner/description/color；updateMask 可选：省略=全部已填充字段、*=全量替换、空串=no-op；未知字段/空路径段 400；lockedVersion 必填，缺失 400；不一致 409；默认工作集只读 409）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "更新成功，返回新 ETag"),
            @ApiResponse(responseCode = "400", description = "未知掩码字段 / 掩码空路径段 / 缺 lockedVersion（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "工作集不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "默认工作集只读 / lockedVersion 不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @ValidUpdateMask(whitelist = {"displayName", "owner", "description", "color"})
    @PatchMapping("/{id}")
    @EtagResource("id")
    public QWorkingset update(@PathVariable("id") String id,
            @Parameter(description = "参与变更的字段清单（可选；省略=更新全部已填充字段，*=全量替换，空串=不更新）", example = "displayName,color") @RequestParam(value = "updateMask", required = false) String updateMask,
            @RequestBody UpdateWorkingsetRequest request) {
        // mock: 待实现任务替换
        return new QWorkingset("workingsets/" + id, id, "示例工作集", "623780000000201", "赵拓", "门禁评审用工作集", "#3B82F6", null,
                true, "SHARED", "head", List.of("623780000000101"), "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "workingsetAddTrackers", summary = "添加 Tracker 至工作集（body branches[]：产品空间×Tracker×分支 SHARED/INCLUDED；已存在分支幂等跳过；201+Location 指向工作集（新分支已挂载）；候选树经 intent=WORKINGSET_EXTEND；幂等键必接）")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "新分支已创建（已存在分支幂等跳过）：Location 指向工作集"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "工作集 / 产品空间 / Tracker 不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "lockedVersion 不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping("/{id}:addTrackers")
    @EtagResource("id")
    public QWorkingset addTrackers(@PathVariable("id") String id, @RequestBody AddTrackersRequest request) {
        // mock: 待实现任务替换
        return new QWorkingset("workingsets/" + id, id, "示例工作集", "623780000000201", "赵拓", "门禁评审用工作集", "#3B82F6", null,
                true, "SHARED", "head", List.of("623780000000101"), "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "workingsetDelete", summary = "删除工作集（204 幂等；删除族不校验版本；默认工作集 409；存在子工作集或已挂分支 409；幂等键必接）")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "删除成功（幂等：资源已不存在仍 204）"),
            @ApiResponse(responseCode = "409", description = "默认工作集 / 存在子工作集 / 已挂分支，状态不允许删除", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @DeleteMapping("/{id}")
    @EtagResource("id")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        new DeleteWorkingsetCommand();
        // mock: 待实现任务替换
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "workingsetReplaceContent", summary = "工作集内容替换（GateFlow 回滚；LRO：202+Location /v1/operations/{operationId}；以源工作集内容替换本工作集；幂等键必接）")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "已受理：Location 指向 /v1/operations/{operationId}，完成后 response 携带替换统计（分支新建/覆盖计数）"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "本工作集或源工作集不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @LroOperation(taskType = "workingsetReplaceContent")
    @PostMapping("/{id}:replaceContent")
    public com.airedgesoft.lib.restful.lro.Operation replaceContent(@PathVariable("id") String id,
            @RequestBody ReplaceWorkingsetContentRequest request) {
        // mock: 待实现任务替换（pending Operation 落 Store，轮询可查；真实实现由方法体创建 + 提交异步任务）
        com.airedgesoft.lib.restful.lro.Operation operation = com.airedgesoft.lib.restful.lro.Operation
                .pending("623809242866910", "workingsetReplaceContent");
        operationStore.save(operation);
        return operation;
    }
}
