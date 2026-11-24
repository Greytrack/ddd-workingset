package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.EnableRepresentationCache;
import com.airedgesoft.sharedkernel.businessframework.application.query.PageViewProjectionPort;
import com.airedgesoft.sharedkernel.businessframework.application.query.QPageView;
import com.airedgesoft.sharedkernel.businessframework.application.query.ResolvePageViewQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面视图入站适配器（只读计算投影；不挂分类层级）——契约出自任务书 §2（2026-11-20）。
 *
 * <p>
 * <b>为何类上不加 {@code @RequestMapping}</b>：集合级冒号方法必须方法级写全路径（规范 §4.2.3）。 PageView 不是持久资源：无 name/id/lockedVersion、无
 * configRevision（用户裁决 2026-11-20 只留 ETag——表示 hash）； 返回三部分：fields（字段：客户配置）+ actions（按钮：operationId
 * 化——DETAIL/EDIT/LIST=客户配置、CREATE=createActions 配置编排）+ layout（布局模板：前端定义，后端按 purpose
 * 从配置原样透传，不解析不修改）。<b>对所有人相同，不含任何用户态</b>。
 */
@RestController
@Tag(name = "共享内核-页面视图", description = "PageView 页面视图解析（只读计算投影：字段 + 按钮 + 布局模板透传）")
public class PageViewController {

    private final PageViewProjectionPort projectionPort;

    public PageViewController(PageViewProjectionPort projectionPort) {
        this.projectionPort = projectionPort;
    }

    @Operation(operationId = "pageViewResolve", summary = "解析页面视图（字段 + 按钮 + 布局模板透传；ETag；无 name/id）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "成功，响应头携带 ETag"),
            @ApiResponse(responseCode = "400", description = "请求参数非法（purpose 取值非法 / classificationId、tabKey 非规范数字）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "分类不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "按钮未命中 operationId 映射等配置欠账（fail-fast；detail 为通用文案，缺失项见服务端日志）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping("/v1/pageViews:resolve")
    @EnableRepresentationCache
    public QPageView resolve(
            @Parameter(description = "分类 id", example = "14010102") @RequestParam(value = "classificationId") String classificationId,
            @Parameter(description = "用途：DETAIL / LIST / EDIT / CREATE", example = "DETAIL") @RequestParam(value = "purpose") QPageView.Purpose purpose,
            @Parameter(description = "页签 id（LIST 可选；取值即 V3 列表 id，传入时并入该页签列表按钮；v3 列表列头为分类级）", example = "420288") @RequestParam(value = "tabKey", required = false) String tabKey,
            @Parameter(description = "表槽位稳定键（LIST 同一页签多表时用；回显，服务端不消费）", example = "alm.requirement.links.upstream") @RequestParam(value = "tableKey", required = false) String tableKey,
            @Parameter(description = "新建形态（CREATE 时：MODAL 默认 / WINDOW）", example = "MODAL") @RequestParam(value = "variant", required = false) QPageView.Variant variant) {
        return projectionPort.resolve(new ResolvePageViewQuery(classificationId, purpose, tabKey, tableKey, variant));
    }
}
