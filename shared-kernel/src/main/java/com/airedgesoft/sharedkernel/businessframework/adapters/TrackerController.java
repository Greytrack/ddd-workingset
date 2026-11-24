package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.EtagResource;
import com.airedgesoft.lib.restful.filter.Filterable;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import com.airedgesoft.sharedkernel.businessframework.application.command.CreateTrackerCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.DeleteTrackerCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.TrackerUndeleteCommand;
import com.airedgesoft.sharedkernel.businessframework.application.query.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tracker 资源入站适配器（左树分类配置、清单、排序、回收站与工作集分支比较/合并）。
 *
 * <p>
 * 添加 Tracker 至工作集落在 {@link WorkingSetController}（{@code :addTrackers}，2026-10-29 收口）；选择树投影在
 * {@link ProductSpaceController}。
 */
@RestController
@Tag(name = "共享内核-Tracker", description = "Tracker 管理：创建/清单/配置读写/删除还原/排序/比较合并")
@RequestMapping("/v1/trackers")
public class TrackerController {

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "trackerCreate", summary = "创建 Tracker（左树 hover「+」；仅默认工作集放开，非默认 409；分类不存在 404；幂等键必接）")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "创建成功：Location 指向新 Tracker，ETag 为响应表示 hash（并发代数见 body lockedVersion）"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "分类不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "非默认工作集不允许新建（权限归 _access）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping
    @EtagResource
    public QTracker create(@RequestBody CreateTrackerCommand command) {
        // mock: 待实现任务替换
        return new QTracker("trackers/623809242866102", "623809242866102", command.displayName(),
                command.trackerClassId(), command.workSpaceId(), command.parentId(), "TABLE", "2026-10-29T08:00:00Z",
                "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "trackerList", summary = "Tracker 配置清单（分页 + filter；空集合 200）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（空集合 200）"),
            @ApiResponse(responseCode = "400", description = "分页参数 / filter 语法非法（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @Filterable({"displayName", "workSpaceId"})
    @GetMapping
    public Page<QTracker> list(
            @Parameter(description = "单页数量（缺省取系统配置）", example = "20") @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @Parameter(description = "下一页游标（首页不传）") @RequestParam(value = "pageToken", required = false) String pageToken,
            @Parameter(description = "AIP-160 过滤（白名单 displayName/workSpaceId）", example = "workSpaceId=\"623780000000101\"") @RequestParam(value = "filter", required = false) String filter) {
        ListTrackersQuery query = new ListTrackersQuery(pageSize, pageToken, filter);
        // mock: 待实现任务替换
        return Page.of(List.of(new QTracker("trackers/623809242866102", "623809242866102", "系统需求", "1003",
                "623780000000101", null, "TABLE", "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z")), null, 1);
    }

    @Operation(operationId = "trackerGet", summary = "Tracker 配置读（ETag；业务读恒 200；不存在 404）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "成功，响应头携带 ETag"),
            @ApiResponse(responseCode = "404", description = "Tracker 不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping("/{id}")
    @EtagResource("id")
    public QTracker get(@PathVariable("id") String id) {
        GetTrackerQuery query = new GetTrackerQuery(id);
        // mock: 待实现任务替换
        return new QTracker("trackers/" + id, id, "系统需求", "1003", "623780000000101", null, "TABLE",
                "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "trackerUpdate", summary = "Tracker 配置编辑（掩码白名单 displayName/instanceTreeShowContent；updateMask 可选：省略=全部已填充字段、*=全量替换、空串=no-op；未知字段/空路径段 400；lockedVersion 必填，缺失 400；不一致 409；幂等键必接）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "更新成功，返回新 ETag"),
            @ApiResponse(responseCode = "400", description = "未知掩码字段 / 掩码空路径段（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "Tracker 不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "lockedVersion 不一致（并发冲突）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @ValidUpdateMask(whitelist = {"displayName", "instanceTreeShowContent"})
    @PatchMapping("/{id}")
    @EtagResource("id")
    public QTracker update(@PathVariable("id") String id,
            @Parameter(description = "参与变更的字段清单（可选；省略=更新全部已填充字段，*=全量替换，空串=不更新）", example = "displayName") @RequestParam(value = "updateMask", required = false) String updateMask,
            @RequestBody UpdateTrackerRequest request) {
        // mock: 待实现任务替换
        return new QTracker("trackers/" + id, id, request.displayName(), "1003", "623780000000101", null, "TABLE",
                "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "trackerDelete", summary = "删除 Tracker（软删入回收站；204 幂等；占用 409）")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "删除成功（幂等：资源已不存在仍 204）"),
            @ApiResponse(responseCode = "409", description = "Tracker 被占用（存在条目或被工作集分支引用）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @DeleteMapping("/{id}")
    @EtagResource("id")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        new DeleteTrackerCommand();
        // mock: 待实现任务替换
    }

    @Operation(operationId = "trackerUndelete", summary = "还原 Tracker（回收站还原；仅已软删生效，否则 404；200+新 ETag）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "还原成功，返回新 ETag"),
            @ApiResponse(responseCode = "404", description = "Tracker 不存在或未软删", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "父分类已删除", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping("/{id}:undelete")
    @EtagResource("id")
    public QTracker undelete(@PathVariable("id") String id) {
        new TrackerUndeleteCommand();
        // mock: 待实现任务替换
        return new QTracker("trackers/" + id, id, "系统需求", "1003", "623780000000101", null, "TABLE",
                "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "trackerMove", summary = "Tracker 拖拽排序（position=BEFORE|AFTER；拖到自身 409；跨分类 409；非默认工作集 409；幂等键必接；lockedVersion 必填，缺失 400、不一致 409）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "排序成功，返回新 ETag"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "Tracker 不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "拖到自身 / 跨分类 / 非默认工作集", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "lockedVersion 不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping("/{id}:move")
    @EtagResource("id")
    public QTracker move(@PathVariable("id") String id, @RequestBody TrackerMoveRequest request) {
        // mock: 待实现任务替换
        return new QTracker("trackers/" + id, id, "系统需求", "1003", "623780000000101", null, "TABLE",
                "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "trackerCompare", summary = "比较工作集（Tracker 一次性对比：body 指定对端工作集；一次下发 Tracker 级字段差异 + 全部条目级差异（CREATED/MODIFIED/DELETED/MOVED）；计算型只读，幂等）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "比较结果（Tracker 级 + 条目级两级差异）"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "Tracker 或对端工作集不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping("/{id}:compare")
    @EtagResource("id")
    public QWorkingsetCompareResult compare(@PathVariable("id") String id, @RequestBody CompareWorkingsetsQuery query) {
        // mock: 待实现任务替换
        return new QWorkingsetCompareResult(id, query.targetWorkingsetId(),
                List.of(new QWorkingsetCompareResult.FieldDiff("displayName", "分类名", "齿轮分类A", "齿轮分类B")),
                List.of(new QWorkingsetCompareResult.ItemDiff("MODIFIED", "R00003", "示例需求", null)));
    }

    @Operation(operationId = "trackerItemCompare", summary = "条目对比（单个 TrackerItem 与对端工作集对应条目的字段级差异；body 指定对端工作集；计算型只读，幂等）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "条目字段级差异"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "Tracker 或条目或对端工作集不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping("/{id}/items/{itemId}:compare")
    @EtagResource("id")
    public QTrackerItemCompareResult compareItem(@PathVariable("id") String id, @PathVariable("itemId") String itemId,
            @RequestBody CompareWorkingsetsQuery query) {
        // mock: 待实现任务替换
        return new QTrackerItemCompareResult(itemId, "R00003", "示例需求", query.targetWorkingsetId(),
                List.of(new QWorkingsetCompareResult.FieldDiff("status", "状态", "DRAFT", "APPROVED")));
    }

    @Operation(operationId = "trackerMerge", summary = "合并工作集（写：把源工作集内容并入当前；body 指定源工作集与范围；双向修改冲突 409+条目明细（两级粒度）；同步执行；幂等键必接）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "合并成功，返回合并后的 Tracker 分支与新 ETag"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "Tracker 或源工作集不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "双向修改冲突（RFC 9457，条目明细随 problem 下发）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "lockedVersion 不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping("/{id}:merge")
    @EtagResource("id")
    public QTracker merge(@PathVariable("id") String id, @RequestBody MergeWorkingsetRequest request) {
        // mock: 待实现任务替换
        return new QTracker("trackers/" + id, id, "系统需求", "1003", "623780000000101", null, "TABLE",
                "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z");
    }
}
