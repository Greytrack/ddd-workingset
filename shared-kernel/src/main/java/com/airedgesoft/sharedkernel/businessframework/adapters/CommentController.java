package com.airedgesoft.sharedkernel.businessframework.adapters;

import com.airedgesoft.lib.restful.etag.EtagResource;
import com.airedgesoft.lib.restful.filter.Filterable;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import com.airedgesoft.sharedkernel.businessframework.application.command.CreateCommentCommand;
import com.airedgesoft.sharedkernel.businessframework.application.command.DeleteCommentCommand;
import com.airedgesoft.sharedkernel.businessframework.application.query.ListCommentsQuery;
import com.airedgesoft.sharedkernel.businessframework.application.query.QComment;
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
 * 评论资源入站适配器（顶级资源，{@code target} 为 AIP-122 资源名——评论不限于需求，评审条目复用同一资源）。
 */
@RestController
@Tag(name = "共享内核-评论", description = "评论顶级资源：按对象查询/发表/修订/删除")
@RequestMapping("/v1/comments")
public class CommentController {

    @Operation(operationId = "commentList", summary = "条目评论列表（按对象查询：filter=target=\"资源名\"；游标分页 Page 信封；orderBy=createTime（id 兜底））")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page 信封返回（空集合 200）"),
            @ApiResponse(responseCode = "400", description = "分页参数 / filter 语法 / 白名单校验失败（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @Filterable({"target", "authorActorId", "createTime"})
    @GetMapping
    public Page<QComment> list(
            @Parameter(description = "单页数量（缺省取系统配置）", example = "20") @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @Parameter(description = "下一页游标（首页不传）") @RequestParam(value = "pageToken", required = false) String pageToken,
            @Parameter(description = "AIP-160 过滤（白名单 target/authorActorId/createTime）", example = "target=\"requirements/623809242866016\"") @RequestParam(value = "filter", required = false) String filter,
            @Parameter(description = "排序（createTime，id 兜底）", example = "createTime asc") @RequestParam(value = "orderBy", required = false) String orderBy) {
        // target 由 filter 解析后填充，mock 阶段置 null
        ListCommentsQuery query = new ListCommentsQuery(pageSize, pageToken, null, orderBy);
        // mock: 待实现任务替换
        return Page.of(List.of(sample("623809242866400")), null, 1);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "commentCreate", summary = "发表评论（含回复；target 为资源名；content 必填非空 400；目标不存在 404/已删 409；201+Location；幂等键必接）")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "发表成功：Location 指向新评论"),
            @ApiResponse(responseCode = "400", description = "content 空 / target 非法（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "目标对象不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "目标对象已删除 / 被回复评论不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key 与请求体不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PostMapping
    @EtagResource
    public QComment create(@RequestBody CreateCommentCommand command) {
        // mock: 待实现任务替换
        return new QComment("comments/623809242866400", "623809242866400", command.target(), command.content(),
                command.replyTo(), "623780000000201", "赵拓", "2026-11-09T08:00:00Z", null);
    }

    @Operation(operationId = "commentUpdate", summary = "修订自己的评论（updateMask 可选：省略=全部已填充字段、*=全量替换、空串=no-op；仅作者本人 403；未知字段/空路径段 400；lockedVersion 必填，缺失 400、不一致 409）")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "修订成功，返回新 ETag"),
            @ApiResponse(responseCode = "400", description = "未知掩码字段 / 掩码空路径段 / content 空 / 缺 lockedVersion（RFC 9457）", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "仅作者本人可修订", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "评论不存在", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "409", description = "lockedVersion 不一致", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @PatchMapping("/{id}")
    @EtagResource("id")
    @ValidUpdateMask(whitelist = {"content"})
    public QComment update(@PathVariable("id") String id,
            @Parameter(description = "参与变更的字段清单（可选；省略=更新全部已填充字段，*=全量替换，空串=不更新）", example = "content") @RequestParam(value = "updateMask", required = false) String updateMask,
            @RequestBody ModifyCommentRequest request) {
        // mock: 待实现任务替换
        return new QComment("comments/" + id, id, "requirements/623809242866016", request.content(), null,
                "623780000000201", "赵拓", "2026-11-09T08:00:00Z", "2026-11-09T08:00:00Z");
    }

    @Operation(operationId = "commentDelete", summary = "删除评论（仅作者或管理员 403；204 幂等；不带版本）")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "删除成功（幂等）"),
            @ApiResponse(responseCode = "403", description = "仅作者或管理员可删除", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))})})
    @DeleteMapping("/{id}")
    @EtagResource("id")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        new DeleteCommentCommand();
        // mock: 待实现任务替换
    }

    private static QComment sample(String id) {
        return new QComment("comments/" + id, id, "requirements/623809242866016", "示例评论内容", null, "623780000000201",
                "赵拓", "2026-11-09T08:00:00Z", null);
    }
}
