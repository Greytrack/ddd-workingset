package com.airedgesoft.sharedkernel.workingset.adapters;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.application.command.MergeExecutionService;
import com.airedgesoft.sharedkernel.workingset.application.command.MergeExecutionService.EntryRequest;
import com.airedgesoft.sharedkernel.workingset.application.query.MergeComparisonService;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Compare / Merge 的 HTTP 入口。
 *
 * <p>Compare 只读瞬时，按 offset/limit 分页，不承诺跨请求冻结。Merge 只接受一页决策的整页提交：
 * requestId 是幂等键，同键返回原轮次、同键换方向返回冲突；轮次由 {@code MergeRoundSubmittedEvent}
 * 异步执行，客户端轮询 {@code GET /api/merge-rounds/{id}} 至终态。
 */
@RestController
@RequestMapping("/api")
public class MergeController {
    /**
     * UI 一页差异约 20–50 条；超出应重新 compare 分页，而不是提交一轮更大的决策。
     */
    private static final int MAX_DECISIONS = 50;
    private static final int MAX_REQUEST_ID = 128;
    private static final int MAX_COMPARE_LIMIT = 500;

    private final MergeComparisonService comparisons;
    private final MergeExecutionService rounds;

    public MergeController(MergeComparisonService comparisons, MergeExecutionService rounds) {
        this.comparisons = comparisons;
        this.rounds = rounds;
    }

    private static void checkPage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_COMPARE_LIMIT) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "offset must be nonnegative and limit must be between 1 and "
                    + MAX_COMPARE_LIMIT);
        }
    }

    private static ComparisonPage page(Comparison comparison, int offset, int limit) {
        int start = Math.min(offset, comparison.differences().size());
        int end = start + Math.min(limit, comparison.differences().size() - start);
        return new ComparisonPage(comparison.source(), comparison.target(), comparison.comparedAt(),
                comparison.candidates(), comparison.differences().subList(start, end),
                end < comparison.differences().size() ? end : null);
    }

    /**
     * 对直属父子工作集的 Tracker 对做只读瞬时比较，按 offset/limit 分页；不承诺跨请求冻结（ADR-0005 补充）。
     */
    @PostMapping("/worksets/{target}/compare/{source}")
    public ComparisonPage compare(@PathVariable Long target,
                                  @PathVariable Long source,
                                  @RequestParam Long sourceTrackerId,
                                  @RequestParam Long targetTrackerId,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(defaultValue = "100") int limit) {
        checkPage(offset, limit);
        Comparison result = comparisons.compare(source, target,
                sourceTrackerId, targetTrackerId);
        return page(result, offset, limit);
    }

    /**
     * 提交一页合并决策（1–50 条）；requestId 为幂等键，轮次异步执行，客户端轮询 {@code GET /api/merge-rounds/{id}}。
     */
    @PostMapping("/worksets/{target}/merge-rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public MergeRound submit(@PathVariable Long target, @RequestBody SubmitRequest request) {
        if (request.sourceWorkingSetId() == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "sourceWorkingSetId is required");
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "requestId is required");
        }
        if (request.requestId().length() > MAX_REQUEST_ID) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "requestId must be at most " + MAX_REQUEST_ID + " characters");
        }
        if (request.decisions() == null || request.decisions().isEmpty()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "decisions must contain at least one entry");
        }
        if (request.decisions().size() > MAX_DECISIONS) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "decisions must contain at most " + MAX_DECISIONS + " entries");
        }
        return rounds.submit(target, request.sourceWorkingSetId(),
                request.requestId(), List.copyOf(request.decisions()), "web");
    }

    /**
     * 触发轮次执行（幂等）；重复调用返回同一轮次的当前 ApplyResult。
     */
    @PostMapping("/merge-rounds/{id}/apply")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MergeRound apply(@PathVariable Long id) {
        return rounds.apply(Long.toString(id));
    }

    /**
     * 按 id 查询轮次状态、条目结果与唯一终态 ApplyResult。
     */
    @GetMapping("/merge-rounds/{id}")
    public MergeRound get(@PathVariable Long id) {
        return rounds.get(Long.toString(id));
    }

    public record SubmitRequest(Long sourceWorkingSetId, String requestId, List<EntryRequest> decisions) {
    }

    public record ComparisonPage(@JsonFormat(shape = JsonFormat.Shape.STRING) Long source, @JsonFormat(shape = JsonFormat.Shape.STRING) Long target, long comparedAt,
                                 List<Comparison.TrackerPair> candidates, List<Comparison.Difference> differences,
                                 Integer nextOffset) {
    }
}
