package sk.workingset.domain.merge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.MergeFailure.Reason;

/**
 * Merge 轮次聚合：登记条目、执行、唯一终态结果；终态不可重开。
 *
 * <p>状态为 Preparing → Running → Succeeded/PartiallySucceeded/Failed，没有独立 Completed 阶段（C5）。
 */
public record MergeRound(String id, String token, WorkingSetId source, WorkingSetId target, String caller,
                         State state, List<Entry> entries, ApplyResult result) {
    public enum State { Preparing, Running, Succeeded, PartiallySucceeded, Failed }
    public enum Outcome { Applied, Confirmed, Rejected, Conflict, StaleEvidence, Deferred, Unknown }

    public record Entry(String id, long decisionVersion, Difference evidence, MergeDecision decision, ItemResult result) {
        Entry finish(ItemResult result) {
            if (this.result != null) {
                throw new MergeFailure(Reason.RoundClosed, id);
            }
            return new Entry(id, decisionVersion, evidence, decision, result);
        }
    }

    public record ItemResult(String entryId, String entryKey, Outcome outcome, String operation,
                             String businessRef, String checkpoint, String failure) {
        public boolean succeeded() {
            return outcome == Outcome.Applied || outcome == Outcome.Confirmed;
        }
    }

    public record Summary(long applied, long confirmed, long rejected, long conflict, long deferred,
                          long staleEvidence, long unknown) {}

    public record ApplyResult(State state, Summary summary, List<ItemResult> itemResults, Instant completedAt) {
        public ApplyResult { itemResults = List.copyOf(itemResults); }
    }

    public MergeRound {
        entries = List.copyOf(entries);
    }

    public static MergeRound prepare(String id, Comparison comparison, String caller) {
        return new MergeRound(id, comparison.token(), comparison.source(), comparison.target(), caller,
                State.Preparing, List.of(), null);
    }

    public MergeRound register(Entry entry) {
        if (state != State.Preparing) {
            throw new MergeFailure(Reason.RoundClosed, id);
        }
        var updated = new ArrayList<>(entries);
        updated.add(entry);
        return new MergeRound(id, token, source, target, caller, state, updated, null);
    }

    public MergeRound start() {
        if (state != State.Preparing) {
            return this;
        }
        return new MergeRound(id, token, source, target, caller, State.Running, entries, null);
    }

    public MergeRound finishEntry(Entry entry, ItemResult itemResult) {
        if (state != State.Running) {
            throw new MergeFailure(Reason.RoundClosed, id);
        }
        var updated = entries.stream().map(candidate -> candidate.id().equals(entry.id())
                ? candidate.finish(itemResult) : candidate).toList();
        return new MergeRound(id, token, source, target, caller, state, updated, null);
    }

    public MergeRound complete(Instant completedAt) {
        if (state != State.Running || entries.stream().anyMatch(entry -> entry.result() == null)) {
            throw new MergeFailure(Reason.RoundClosed, id);
        }
        var results = entries.stream().map(Entry::result).toList();
        long successful = results.stream().filter(ItemResult::succeeded).count();
        State terminal = successful == 0 ? State.Failed
                : successful == results.size() ? State.Succeeded : State.PartiallySucceeded;
        var summary = new Summary(count(results, Outcome.Applied), count(results, Outcome.Confirmed),
                count(results, Outcome.Rejected),
                count(results, Outcome.Conflict), count(results, Outcome.Deferred), count(results, Outcome.StaleEvidence),
                count(results, Outcome.Unknown));
        return new MergeRound(id, token, source, target, caller, terminal, entries,
                new ApplyResult(terminal, summary, results, completedAt));
    }

    private static long count(List<ItemResult> results, Outcome outcome) {
        return results.stream().filter(result -> result.outcome() == outcome).count();
    }
}
