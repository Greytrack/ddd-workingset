package com.airedgesoft.sharedkernel.workingset.adapters;

import com.airedgesoft.lib.restful.etag.EtagResource;
import com.airedgesoft.lib.restful.lro.LroOperation;
import com.airedgesoft.lib.restful.lro.OperationStore;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import com.airedgesoft.sharedkernel.workingset.application.command.CreateWorkingsetCommand;
import com.airedgesoft.sharedkernel.workingset.application.command.DeleteWorkingsetCommand;
import com.airedgesoft.sharedkernel.businessframework.application.query.GetWorkingsetQuery;
import com.airedgesoft.sharedkernel.businessframework.application.query.ListWorkingsetsQuery;
import com.airedgesoft.sharedkernel.businessframework.application.query.QWorkingset;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.SharingMode;
import com.airedgesoft.sharedkernel.workingset.adapters.self.CreateChildRequest;
import com.airedgesoft.sharedkernel.workingset.application.command.WorkingSetCommandService;
import com.airedgesoft.sharedkernel.workingset.application.command.WorkingSetDeletionService;
import com.airedgesoft.sharedkernel.workingset.application.query.WorkingSetQueryService;
import com.airedgesoft.sharedkernel.workingset.application.query.model.*;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
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
 * 本类为工作集的唯一入站适配器：路由以团队定稿的 {@code /v1/workingsets} 契约为准；详情读取、编辑与删除已接入
 * {@link WorkingSetQueryService} / {@link WorkingSetCommandService} / {@link WorkingSetDeletionService} 的真实实现，
 * 列表 / 创建 / 添加 Tracker / 内容替换暂无应用服务支撑，保留契约形状的 mock 实现（待实现任务替换）。
 *
 * <p>
 * 向导树投影落在 {@code ProductSpaceController}；比较/合并已迁 trackers 族（{@code TrackerController}）。
 *
 * <p>
 * 文末保留原 {@code /api/worksets} 实现的方法体与读模型（已去除映射注解、不作为端点暴露），供契约核对；确认后删除。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "共享内核-工作集", description = "工作集管理：列表/创建/编辑/删除/添加 Tracker/内容替换")
@RequestMapping("/v1/workingsets")
public class WorkingSetController {

    private final OperationStore operationStore;
    private final WorkingSetCommandService commands;
    private final WorkingSetQueryService queries;
    private final WorkingSetDeletionService deletions;

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
        // mock: 待实现任务替换（暂无工作集列表查询服务）
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
        // mock: 待实现任务替换（真实创建为异步任务，见 WorkingSetCreationController）
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
        return toView(queries.find(Long.valueOf(id)));
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
        // displayName / color 已接入真实编辑；owner / description 暂无应用服务支撑，保持原值不动。
        return toView(commands.updatePresentation(Long.valueOf(id), request.displayName(), request.color()));
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
        // mock: 待实现任务替换（绑定写入目前仅在工作集创建任务内可用）
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
        deletions.deleteWorkingSet(Long.valueOf(id));
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

    // ==================== 原 /api/worksets 实现（保留供核对；已去除映射注解，不作为端点暴露） ====================

    /**
     * 原 {@code GET /api/worksets/{workingSetId}}：按 id 查询工作集；创建中的目标工作集返回 Preparing。
     */
    public WorkingSetView find(Long workingSetId) {
        return WorkingSetView.from(queries.find(workingSetId));
    }

    /**
     * 原 {@code DELETE /api/worksets/{workingSetId}}。
     */
    public void deleteWorkingSet(Long workingSetId) {
        deletions.deleteWorkingSet(workingSetId);
    }

    /**
     * 原 {@code PATCH /api/worksets/{workingSetId}}：编辑工作集显示名称与主题色；请求中缺失的字段保持不变。
     */
    public WorkingSetView updatePresentation(Long workingSetId, com.airedgesoft.sharedkernel.workingset.adapters.self.UpdateWorkingSetRequest request) {
        return WorkingSetView.from(commands.updatePresentation(workingSetId,
                request.name(), request.color()));
    }

    /**
     * 原 {@code POST /api/worksets/{workingSetId}/children}：创建子工作集，形成单父谱系。
     */
    public WorkingSetView createChild(Long workingSetId, CreateChildRequest request) {
        return WorkingSetView.from(commands.createChildWorkingSet(workingSetId,
                request.name(), request.color()));
    }

    /**
     * 原 {@code GET /api/worksets/{workingSetId}/children}：查询直接子工作集的 id 列表。
     */
    public List<String> findChildren(Long workingSetId) {
        return queries.findChildren(workingSetId).stream().map(String::valueOf).toList();
    }

    /**
     * 原 {@code GET /api/worksets/{workingSetId}/ancestors}：查询祖先工作集的 id 列表。
     */
    public List<String> findAncestors(Long workingSetId) {
        return queries.findAncestors(workingSetId).stream().map(String::valueOf).toList();
    }

    /**
     * 原 {@code GET /api/worksets/{workingSetId}/trackers}：工作集内的 Tracker（含 parentId/sourceId，用于构建 Tracker 树）。
     */
    public List<TrackerView> findTrackers(Long workingSetId) {
        return queries.findTrackers(workingSetId).stream().map(TrackerView::from).toList();
    }

    /**
     * 原 {@code GET /api/worksets/metadata/classifications}：可用的分类元数据（含共享特性）。
     */
    public List<ClassificationView> findClassifications() {
        return queries.findClassifications().stream().map(ClassificationView::from).toList();
    }

    /**
     * 原 {@code GET /api/worksets/{workingSetId}/bindings}：工作集的空间绑定与 Tracker 绑定。
     */
    public BindingsView listBindings(Long workingSetId) {
        return BindingsView.from(queries.findBindings(workingSetId));
    }

    /** v1 读模型：由工作集摘要映射；v1 契约的 owner / description / branchType 等暂无聚合事实，留空。 */
    private static QWorkingset toView(WorkingSetSummary summary) {
        Long parentId = summary.parentWorkingSetId();
        return new QWorkingset("workingsets/" + summary.workingSetId(), String.valueOf(summary.workingSetId()),
                summary.name(), null, null, null, summary.color(),
                parentId == null ? null : String.valueOf(parentId), true, null, null, List.of(), null, null);
    }

    /** v1 读模型：由工作集聚合映射（编辑出参）。 */
    private static QWorkingset toView(WorkingSet workingSet) {
        Long parentId = workingSet.parentWorkingSetId();
        return new QWorkingset("workingsets/" + workingSet.id(), String.valueOf(workingSet.id()), workingSet.name(),
                workingSet.owner() == null ? null : String.valueOf(workingSet.owner()), null, null, workingSet.color(),
                parentId == null ? null : String.valueOf(parentId), true, null, null,
                workingSet.spaces().stream().map(space -> space.productSpaceId()).toList(), null, null);
    }

    public record WorkingSetView(String workingSetId, String name, boolean defaultWorkingSet,
                                 String parentWorkingSetId, String color) {
        static WorkingSetView from(WorkingSet workingSet) {
            Long parentId = workingSet.parentWorkingSetId();
            return new WorkingSetView(Long.toString(workingSet.id()), workingSet.name(),
                    WorkingSet.DEFAULT_ID.equals(workingSet.id()),
                    parentId == null ? null : Long.toString(parentId), workingSet.color());
        }

        static WorkingSetView from(WorkingSetSummary summary) {
            return new WorkingSetView(String.valueOf(summary.workingSetId()), summary.name(),
                    summary.defaultWorkingSet(),
                    summary.parentWorkingSetId() == null ? null : String.valueOf(summary.parentWorkingSetId()),
                    summary.color());
        }
    }

    public record ClassificationView(String classificationId, SharingMode sharingMode, String name,
                                     String baseClassId, String classId) {
        static ClassificationView from(ClassificationSummary summary) {
            return new ClassificationView(String.valueOf(summary.classificationId()),
                    SharingMode.valueOf(summary.sharingMode()), summary.name(),
                    String.valueOf(summary.baseClassId()), String.valueOf(summary.classId()));
        }
    }

    public record SpaceBindingView(String productSpaceId, String name, String origin, String state) {
        static SpaceBindingView from(SpaceBindingSummary summary) {
            return new SpaceBindingView(String.valueOf(summary.productSpaceId()), summary.name(),
                    summary.origin(), summary.state());
        }
    }

    public record TrackerBindingView(String trackerId, String productSpaceId, String classificationId,
                                     String sharingMode, String origin, boolean shown) {
        static TrackerBindingView from(TrackerBindingSummary summary) {
            return new TrackerBindingView(String.valueOf(summary.trackerId()),
                    String.valueOf(summary.productSpaceId()), String.valueOf(summary.classificationId()),
                    summary.sharingMode(), summary.origin(), summary.shown());
        }
    }

    public record BindingsView(List<SpaceBindingView> spaces, List<TrackerBindingView> trackers) {
        static BindingsView from(WorkingSetBindings model) {
            return new BindingsView(model.spaces().stream().map(SpaceBindingView::from).toList(),
                    model.trackers().stream().map(TrackerBindingView::from).toList());
        }
    }

    public record TrackerView(String trackerId, String name, String parentId, String sourceId, String productSpaceId,
                              String classificationId, String sharingMode, boolean isDefault) {
        static TrackerView from(TrackerSummary summary) {
            return new TrackerView(String.valueOf(summary.trackerId()), summary.name(),
                    summary.parentId() == null ? null : String.valueOf(summary.parentId()),
                    summary.sourceId() == null ? null : String.valueOf(summary.sourceId()),
                    String.valueOf(summary.productSpaceId()), String.valueOf(summary.classificationId()),
                    summary.sharingMode(), summary.isDefault());
        }
    }
}
