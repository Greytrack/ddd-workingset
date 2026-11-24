package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.EtagResource;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.sharedkernel.businessframework.application.query.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品空间资源入站适配器（只读 + 分类 Tracker 树投影）。
 *
 * <p>
 * 本轮产品空间只读（写侧为反例行不做，model.md §0.3 反例行）；空间级操作菜单沿 v3。
 */
@RestController
@Tag(name = "共享内核-产品空间", description = "产品空间只读（列表/详情/分类 Tracker 树投影）")
@RequestMapping("/v1/productSpaces")
public class ProductSpaceController {

    @Operation(operationId = "productSpaceList", summary = "产品空间列表（只读；写侧为反例行不做）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（空集合 200）")})
    @GetMapping
    public Page<QProductSpace> list() {
        new ListProductSpacesQuery();
        // mock: 待实现任务替换
        return Page.of(List.of(new QProductSpace("productSpaces/623780000000101", "623780000000101", "示例产品空间",
                "门禁评审演示空间", "2026-10-29T08:00:00Z", "2026-10-29T08:00:00Z")), null, 1);
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
}
