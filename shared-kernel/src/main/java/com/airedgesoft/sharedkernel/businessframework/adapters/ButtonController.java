package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.sharedkernel.businessframework.application.query.ListButtonsQuery;
import com.airedgesoft.sharedkernel.businessframework.application.query.QButton;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 按钮资源入站适配器（只读投影：顶栏按钮与操作菜单的可见性、可用性）。
 *
 * <p>
 * 端点保留、不外折（决策 C3）；{@code targetType} + {@code targetId} 已统一为单一 {@code target} 资源名。
 */
@RestController
@Tag(name = "共享内核-按钮", description = "按钮/操作列表投影：按 target×状态×用户返回可用操作")
@RequestMapping("/v1/buttons")
public class ButtonController {

    @Operation(operationId = "buttonList", summary = "按钮/操作列表查询（按 target×状态×用户投影：key/名称/排序/禁用原因）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（disabledReason 为空 = 可用；空集合 200）"),
            @ApiResponse(responseCode = "400", description = "target 缺失或非法（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @GetMapping
    public Page<QButton> list(
            @Parameter(description = "目标资源名（必填）", example = "requirements/623809242866016") @RequestParam(value = "target") String target) {
        ListButtonsQuery query = new ListButtonsQuery(target);
        // mock: 待实现任务替换
        return Page.of(List.of(new QButton("checkout", "签出", 10, null), new QButton("delete", "删除", 40, "存在下游引用，不可删除")),
                null, 2);
    }
}
