package bootstrap.web;

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
import sk.workingset.application.MergeExecutionService.Registration;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeCheckpoint;

@RestController
@RequestMapping("/api")
public class MergeController {
    private static final Caller CALLER = Caller.of("web");

    private final MergeComparisonService comparisons;
    private final MergeExecutionService rounds;

    public MergeController(MergeComparisonService comparisons, MergeExecutionService rounds) {
        this.comparisons = comparisons;
        this.rounds = rounds;
    }

    @PostMapping("/worksets/{target}/compare/{source}")
    public ComparisonPage compare(@PathVariable String target, @PathVariable String source,
                                  @RequestParam(required = false) String trackerId,
                                  @RequestParam(defaultValue = "100") int limit) {
        checkPage(0, limit);
        var result = comparisons.compare(WorkingSetId.of(source), WorkingSetId.of(target),
                trackerId == null ? null : TrackerId.of(trackerId), CALLER);
        return page(result, 0, limit);
    }

    @GetMapping("/comparisons/{token}")
    public ComparisonPage readPage(@PathVariable String token, @RequestParam(defaultValue = "0") int offset,
                                   @RequestParam(defaultValue = "100") int limit) {
        checkPage(offset, limit);
        return page(comparisons.find(token), offset, limit);
    }

    @PostMapping("/worksets/{target}/merge-rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public MergeRound start(@PathVariable String target, @RequestBody StartRequest request) {
        if (request.comparisonToken() == null || request.comparisonToken().isBlank()) {
            throw new IllegalArgumentException("comparisonToken is required");
        }
        return rounds.start(WorkingSetId.of(target), request.comparisonToken(), CALLER);
    }

    @PostMapping("/merge-rounds/{id}/entries")
    public List<Registration> register(@PathVariable String id, @RequestBody EntriesRequest request) {
        if (request.entries() == null) {
            throw new IllegalArgumentException("entries are required");
        }
        return rounds.register(id, List.copyOf(request.entries()));
    }

    @PostMapping("/merge-rounds/{id}/apply")
    public MergeRound.ApplyResult apply(@PathVariable String id) {
        return rounds.apply(id);
    }

    @GetMapping("/merge-rounds/{id}")
    public MergeRound get(@PathVariable String id) {
        return rounds.get(id);
    }

    @PostMapping("/worksets/{target}/merge-checkpoints/{source}/{family}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String target, @PathVariable String source, @PathVariable String family,
                       @RequestBody RevokeRequest request) {
        rounds.revokeCheckpoint(new MergeCheckpoint.Key(WorkingSetId.of(source), WorkingSetId.of(target),
                CommonId.of(family)), request.expectedDecisionVersion());
    }

    private static void checkPage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("offset must be nonnegative and limit must be between 1 and 500");
        }
    }

    private static ComparisonPage page(Comparison comparison, int offset, int limit) {
        int start = Math.min(offset, comparison.differences().size());
        int end = start + Math.min(limit, comparison.differences().size() - start);
        return new ComparisonPage(comparison.token(), comparison.source(), comparison.target(), comparison.comparedAt(),
                comparison.candidates(), comparison.differences().subList(start, end),
                end < comparison.differences().size() ? end : null);
    }

    public record StartRequest(String comparisonToken) {}
    public record RevokeRequest(long expectedDecisionVersion) {}
    public record EntriesRequest(List<EntryRequest> entries) {}
    public record ComparisonPage(String comparisonToken, WorkingSetId source, WorkingSetId target, Instant comparedAt,
                                 List<Comparison.TrackerPair> candidates, List<Comparison.Difference> differences,
                                 Integer nextOffset) {}
}
