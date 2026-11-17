package sk.workingset.adapters;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sk.workingset.application.Caller;
import sk.workingset.application.MergeComparisonService;
import sk.workingset.application.MergeExecutionService;
import sk.workingset.application.MergeExecutionService.EntryRequest;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;

/**
 * Compare / Merge 的 HTTP 入口。
 *
 * <p>Compare 只读瞬时，按 offset/limit 分页，不承诺跨请求冻结。Merge 只接受一页决策的整页提交：
 * requestId 是幂等键，同键返回原轮次、同键换方向返回冲突；轮次由 {@code MergeRoundSubmitted}
 * 异步执行，客户端轮询 {@code GET /api/merge-rounds/{id}} 至终态。
 */
@RestController
@RequestMapping("/api")
public class MergeController {
    private static final Caller CALLER = Caller.of("web");
    /** UI 一页差异约 20–50 条；超出应重新 compare 分页，而不是提交一轮更大的决策。 */
    private static final int MAX_DECISIONS = 50;
    private static final int MAX_REQUEST_ID = 128;
    private static final int MAX_COMPARE_LIMIT = 500;

    private final MergeComparisonService comparisons;
    private final MergeExecutionService rounds;

    public MergeController(MergeComparisonService comparisons, MergeExecutionService rounds) {
        this.comparisons = comparisons;
        this.rounds = rounds;
    }

    @PostMapping("/worksets/{target}/compare/{source}")
    public ComparisonPage compare(@PathVariable String target,
                                  @PathVariable String source,
                                  @RequestParam String sourceTrackerId,
                                  @RequestParam String targetTrackerId,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(defaultValue = "100") int limit) {
        checkPage(offset, limit);
        var result = comparisons.compare(WorkingSetId.of(source), WorkingSetId.of(target),
                TrackerId.of(sourceTrackerId), TrackerId.of(targetTrackerId), CALLER);
        return page(result, offset, limit);
    }

    @PostMapping("/worksets/{target}/merge-rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public MergeRound submit(@PathVariable String target, @RequestBody SubmitRequest request) {
        if (request.sourceWorkingSetId() == null || request.sourceWorkingSetId().isBlank()) {
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
        return rounds.submit(WorkingSetId.of(target), WorkingSetId.of(request.sourceWorkingSetId()),
                request.requestId(), List.copyOf(request.decisions()), CALLER);
    }

    @PostMapping("/merge-rounds/{id}/apply")
    public MergeRound.ApplyResult apply(@PathVariable String id) {
        return rounds.apply(id);
    }

    @GetMapping("/merge-rounds/{id}")
    public MergeRound get(@PathVariable String id) {
        return rounds.get(id);
    }

    @PostMapping("/merge-checkpoints/{trackerA}/{trackerB}/{family}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String trackerA, @PathVariable String trackerB, @PathVariable String family,
                       @RequestBody RevokeRequest request) {
        rounds.revokeCheckpoint(new MergeCheckpoint.Key(TrackerId.of(trackerA), TrackerId.of(trackerB),
                CommonId.of(family)), request.expectedDecisionVersion());
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

    public record SubmitRequest(String sourceWorkingSetId, String requestId, List<EntryRequest> decisions) {}
    public record RevokeRequest(long expectedDecisionVersion) {}
    public record ComparisonPage(WorkingSetId source, WorkingSetId target, Instant comparedAt,
                                 List<Comparison.TrackerPair> candidates, List<Comparison.Difference> differences,
                                 Integer nextOffset) {}
}
