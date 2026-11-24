package com.airedgesoft.sharedkernel.productspace.adapters;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.etag.EtagResource;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.sharedkernel.businessframework.application.query.*;
import com.airedgesoft.sharedkernel.productspace.application.command.ProductSpaceCommandService;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.workingset.application.command.WorkingSetCommandService;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
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
 * 产品空间资源入站适配器（只读 + 分类 Tracker 树投影，并合并创建 / 删除 / Tracker 展示开关）。
 *
 * <p>
 * 本类为产品空间的唯一入站适配器：路由以团队定稿的 {@code /v1/productSpaces} 契约为准；v1 契约没有的创建、删除与
 * Tracker 展示开关从原 {@code /api} 实现合并进来。只读端点暂无查询应用服务，保留契约形状的 mock 实现（待实现任务替换）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "共享内核-产品空间", description = "产品空间（列表/详情/分类 Tracker 树投影/创建/删除/展示开关）")
@RequestMapping("/v1/productSpaces")
public class ProductSpaceController {

    private final ProductSpaceCommandService commands;
    private final WorkingSetCommandService workingSets;

    @Operation(operationId = "productSpaceList", summary = "产品空间列表（只读）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（空集合 200）")})
    @GetMapping
    public Page<QProductSpace> list() {
        new ListProductSpacesQuery();
        // mock: 待实现任务替换（暂无产品空间列表查询服务）
        return Page.of(List.of(new QProductSpace("productSpaces/623780000000101", "623780000000101", "示例产品空间",
                "门禁评审演示空间", "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z")), null, 1);
    }

    @Operation(operationId = "productSpaceCreate", summary = "创建产品空间（v1 契约外的写端点，合并自原 /api 实现；名称去首尾空白后不得为空）")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "创建成功，返回新产品空间"),
            @ApiResponse(responseCode = "400", description = "名称为空（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QProductSpace create(@RequestBody CreateProductSpaceRequest request) {
        ProductSpace space = commands.create(request.name());
        return new QProductSpace("productSpaces/" + space.id(), space.id(), space.name(), null, null, null);
    }

    @Operation(operationId = "productSpaceGet", summary = "产品空间详情（概览/职能团队/子项/分类/操作记录页签；职能团队与子项页签沿 v3）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "成功，响应头携带 ETag"),
            @ApiResponse(responseCode = "404", description = "产品空间不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping("/{id}")
    @EtagResource("id")
    public QProductSpace get(@PathVariable("id") String id) {
        GetProductSpaceQuery query = new GetProductSpaceQuery(id);
        // mock: 待实现任务替换
        return new QProductSpace("productSpaces/" + id, id, "示例产品空间", "门禁评审演示空间", "2026-10-29T08:00:00Z",
                "2026-10-29T08:00:00Z");
    }

    @Operation(operationId = "productSpaceDelete", summary = "删除空产品空间（v1 契约外的写端点，合并自原 /api 实现；存在引用 / Tracker / 工作集绑定时 409）")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "产品空间不存在（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "空间仍被引用（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        commands.delete(id);
    }

    @Operation(operationId = "productSpaceSetTrackerShown", summary = "切换 Tracker 在产品空间中的展示状态（v1 契约外的写端点，合并自原 /api 实现）")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "已切换"),
            @ApiResponse(responseCode = "400", description = "shown 缺失（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "工作集 / 产品空间 / Tracker 不存在（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PatchMapping("/{spaceId}/trackers/{trackerId}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setTrackerShown(@PathVariable Long spaceId, @PathVariable Long trackerId,
            @Parameter(description = "工作集 id") @RequestParam("workingSetId") Long workingSetId,
            @RequestBody TrackerVisibilityRequest request) {
        if (request.shown() == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "shown is required");
        }
        workingSets.setTrackerShown(workingSetId, spaceId, trackerId, request.shown());
    }

    @Operation(operationId = "productSpaceTrackerTree", summary = "分类 Tracker 树投影（向导树 / 左树共用：平铺集合；intent 区分语境，附加可加入性/分支信息）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（平铺节点，按 parentId 组树；投影支持 ETag）"),
            @ApiResponse(responseCode = "404", description = "产品空间不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping("/{id}/trackerTree")
    @EtagResource("id")
    public Page<QTrackerNode> trackerTree(@PathVariable("id") String id,
            @Parameter(description = "投影语境：WORKINGSET_CREATE（向导第 2 步）/ WORKINGSET_EXTEND（添加产品）；缺省 = 左树") @RequestParam(value = "intent", required = false) String intent,
            @Parameter(description = "工作集 id（树语境；可选）") @RequestParam(value = "workingsetId", required = false) String workingsetId,
            @Parameter(description = "基线 id（基线语境；可选）") @RequestParam(value = "baselineId", required = false) String baselineId,
            @Parameter(description = "关键字过滤（可选）") @RequestParam(value = "searchKey", required = false) String searchKey) {
        ProductSpaceTrackerTreeQuery query = new ProductSpaceTrackerTreeQuery(intent, workingsetId, baselineId,
                searchKey);
        // mock: 待实现任务替换
        return Page.of(List.of(
                new QTrackerNode("trackers/623809242866100", "623809242866100", "系统需求", null, 12, false, true, true,
                        "TABLE", true, null),
                new QTrackerNode("trackers/623809242866101", "623809242866101", "硬件需求", "623809242866100", 3, true,
                        true, false, "DOCUMENT", false, "INCLUDED")),
                null, 2);
    }

    /** 产品空间创建请求（合并自原 /api 实现）。 */
    public record CreateProductSpaceRequest(String name) {
    }

    /** Tracker 展示开关请求（合并自原 /api 实现）。 */
    public record TrackerVisibilityRequest(Boolean shown) {
    }
}
