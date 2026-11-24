package com.airedgesoft.sharedkernel.tracker.adapters;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.idempotency.IdempotencyFilter;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.pagination.PageSizeResolver;
import com.airedgesoft.lib.restful.pagination.PageTokens;
import com.airedgesoft.sharedkernel.workingset.adapters.WorkingsetRequestKey;
import com.airedgesoft.sharedkernel.workingset.application.command.MergeExecutionService.EntryRequest;
import com.airedgesoft.sharedkernel.workingset.application.command.MergeExecutionService;
import com.airedgesoft.sharedkernel.workingset.application.query.MergeComparisonService;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Compare / Merge 的 HTTP 入口。
 *
 * <p>Compare 只读瞬时，按 offset/limit 分页，不承诺跨请求冻结。Merge 只接受一页决策的整页提交：
 * requestId 是幂等键，同键返回原轮次、同键换方向返回冲突；轮次由 {@code MergeRoundSubmittedEvent}
 * 异步执行，客户端轮询 {@code GET /v1/mergeRounds/{id}} 至终态。
 */
@RestController
@ApiResponse(responseCode = "400", description = "请求参数无效", content =
        @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "资源不存在", content =
        @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@RequestMapping("/v1")
public class MergeController {
    /**
     * UI 一页差异约 20–50 条；超出应重新 compare 分页，而不是提交一轮更大的决策。
     */
    private static final int MAX_DECISIONS = 50;

    private final MergeComparisonService comparisons;
    private final MergeExecutionService rounds;
    private final PageTokens pageTokens;
    private final PageSizeResolver pageSizes;

    public MergeController(MergeComparisonService comparisons, MergeExecutionService rounds,
            PageTokens pageTokens, PageSizeResolver pageSizes) {
        this.comparisons = comparisons;
        this.rounds = rounds;
        this.pageTokens = pageTokens;
        this.pageSizes = pageSizes;
    }

    /**
     * 对直属父子工作集的 Tracker 对做瞬时比较。分页游标限定在当前比较方向和 Tracker 对内。
     */
    @Operation(operationId = "workingsetCompare", summary = "比较工作集中的 Tracker")
    @ApiResponse(responseCode = "200", description = "比较工作集中的 Tracker")
    @PostMapping("/workingsets/{target}:compare")
    public ComparisonPage compare(@PathVariable("target") Long target,
                                  @RequestParam("sourceWorkingSetId") Long source,
                                  @RequestParam("sourceTrackerId") Long sourceTrackerId,
                                  @RequestParam("targetTrackerId") Long targetTrackerId,
                                  @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                  @RequestParam(name = "pageToken", required = false) String pageToken) {
        int limit = pageSizes.resolve(pageSize);
        String scope = "workingsetCompare:" + source + ":" + target + ":" + sourceTrackerId + ":" + targetTrackerId;
        Long offset = pageTokens.parse(pageToken, scope);
        Comparison result = comparisons.compare(source, target, sourceTrackerId, targetTrackerId);
        int total = result.differences().size();
        int start = offset == null ? 0 : (int) Math.min(offset, total);
        int end = start + Math.min(limit, total - start);
        String next = end < total ? pageTokens.nextToken((long) end, scope) : null;
        return new ComparisonPage(source, target, Instant.ofEpochMilli(result.comparedAt()), result.candidates(),
                Page.of(result.differences().subList(start, end), next, total));
    }

    /**
     * 提交一页合并决策（1–50 条）；requestId 为幂等键，轮次异步执行，客户端轮询 {@code GET /v1/mergeRounds/{id}}。
     */
    @Operation(operationId = "mergeRoundCreate", summary = "登记合并轮次")
    @ApiResponse(responseCode = "201", description = "登记合并轮次")
    @PostMapping("/workingsets/{target}/mergeRounds")
    public ResponseEntity<QMergeRound> submit(@PathVariable("target") Long target,
            @RequestHeader(name = IdempotencyFilter.KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody SubmitRequest request) {
        if (request.sourceWorkingSetId() == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "sourceWorkingSetId is required");
        }
        String key = WorkingsetRequestKey.resolve(idempotencyKey, request.requestId());
        if (request.decisions() == null || request.decisions().isEmpty()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "decisions must contain at least one entry");
        }
        if (request.decisions().size() > MAX_DECISIONS) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "decisions must contain at most " + MAX_DECISIONS + " entries");
        }
        MergeRound round = rounds.submit(target, request.sourceWorkingSetId(),
                key, List.copyOf(request.decisions()), "web");
        return ResponseEntity.created(roundLocation(round)).body(QMergeRound.from(round));
    }

    /**
     * 触发轮次执行（幂等）；重复调用返回同一轮次的当前 ApplyResult。
     */
    @Operation(operationId = "mergeRoundApply", summary = "触发合并轮次执行")
    @ApiResponse(responseCode = "202", description = "触发合并轮次执行")
    @PostMapping("/mergeRounds/{id}:apply")
    public ResponseEntity<QMergeRound> apply(@PathVariable("id") Long id) {
        MergeRound round = rounds.apply(id.toString());
        return ResponseEntity.accepted().location(roundLocation(round)).body(QMergeRound.from(round));
    }

    /**
     * 按 id 查询轮次状态、条目结果与唯一终态 ApplyResult。
     */
    @Operation(operationId = "mergeRoundGet", summary = "读取合并轮次状态和结果")
    @ApiResponse(responseCode = "200", description = "读取合并轮次状态和结果")
    @GetMapping("/mergeRounds/{id}")
    public QMergeRound get(@PathVariable("id") Long id) {
        return QMergeRound.from(rounds.get(id.toString()));
    }

    public record SubmitRequest(@Schema(type = "string") Long sourceWorkingSetId,
                                @Schema(description = "旧客户端的幂等键", deprecated = true) String requestId,
                                List<EntryRequest> decisions) {
    }

    private static URI roundLocation(MergeRound round) {
        return URI.create("/v1/mergeRounds/" + round.id());
    }

    public record ComparisonPage(@JsonFormat(shape = JsonFormat.Shape.STRING) Long source,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) Long target,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) Instant compareTime,
                                 List<Comparison.TrackerPair> candidates,
                                 Page<Comparison.Difference> page) {
        public List<Comparison.Difference> differences() {
            return page.items();
        }
    }

    public record QMergeRound(String name, String id, String token, String requestId, String source,
                              String target, String caller, MergeRound.State state,
                              List<MergeRound.Entry> entries, MergeRound.ApplyResult result) {
        static QMergeRound from(MergeRound round) {
            return new QMergeRound("mergeRounds/" + round.id(), round.id(), round.token(), round.requestId(),
                    round.source().toString(), round.target().toString(), round.caller(), round.state(),
                    round.entries(), round.result());
        }
    }
}
