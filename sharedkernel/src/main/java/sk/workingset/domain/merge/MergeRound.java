package sk.workingset.domain.merge;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.ValueObject;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.MergeFailure.Reason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Merge 轮次聚合：登记条目、执行、唯一终态结果；终态不可重开。
 *
 * <p>状态为 Preparing → Running → Succeeded/PartiallySucceeded/Failed，没有独立 Completed 阶段（C5）。
 */
@AggregateRoot
public record MergeRound(@JsonProperty("id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericId, String token,
                         String requestId, WorkingSetId source, WorkingSetId target,
                         String caller, State state, List<Entry> entries, ApplyResult result) {
    public MergeRound {
        Objects.requireNonNull(requestId, "requestId");
        entries = List.copyOf(entries);
    }

    public static MergeRound prepare(String id, String requestId, WorkingSetId source, WorkingSetId target,
                                     String caller) {
        return new MergeRound(Long.valueOf(id), UUID.randomUUID().toString(), requestId, source, target, caller,
                State.Preparing, List.of(), null);
    }

    private static long count(List<ItemResult> results, Outcome outcome) {
        return results.stream().filter(result -> result.outcome() == outcome).count();
    }

    public String id() {
        return numericId.toString();
    }

    public MergeRound register(Entry entry) {
        if (state != State.Preparing) {
            throw new MergeFailure(Reason.RoundClosed, id());
        }
        ArrayList<MergeRound.Entry> updated = new ArrayList<>(entries);
        updated.add(entry);
        return new MergeRound(numericId, token, requestId, source, target, caller, state, updated, null);
    }

    public MergeRound start() {
        if (state != State.Preparing) {
            return this;
        }
        return new MergeRound(numericId, token, requestId, source, target, caller, State.Running, entries, null);
    }

    public MergeRound finishEntry(Entry entry, ItemResult itemResult) {
        if (state != State.Running) {
            throw new MergeFailure(Reason.RoundClosed, id());
        }
        List<MergeRound.Entry> updated = entries.stream().map(candidate -> candidate.id().equals(entry.id())
                ? candidate.finish(itemResult) : candidate).toList();
        return new MergeRound(numericId, token, requestId, source, target, caller, state, updated, null);
    }

    public MergeRound request(String entryId) {
        List<Entry> updated = entries.stream().map(candidate -> candidate.id().equals(entryId)
                ? candidate.request() : candidate).toList();
        return new MergeRound(numericId, token, requestId, source, target, caller, state, updated, result);
    }

    public boolean awaitingResults() {
        return entries.stream().anyMatch(entry -> entry.result() == null);
    }

    public MergeRound complete(Instant completedAt) {
        if (state != State.Running || entries.stream().anyMatch(entry -> entry.result() == null)) {
            throw new MergeFailure(Reason.RoundClosed, id());
        }
        List<MergeRound.ItemResult> results = entries.stream().map(Entry::result).toList();
        long successful = results.stream().filter(ItemResult::succeeded).count();
        State terminal = successful == 0 ? State.Failed
                : successful == results.size() ? State.Succeeded : State.PartiallySucceeded;
        MergeRound.Summary summary = new Summary(count(results, Outcome.Applied), count(results, Outcome.Confirmed),
                count(results, Outcome.Rejected),
                count(results, Outcome.Conflict), count(results, Outcome.Deferred), count(results, Outcome.StaleEvidence),
                count(results, Outcome.Unknown));
        return new MergeRound(numericId, token, requestId, source, target, caller, terminal, entries,
                new ApplyResult(terminal, summary, results, completedAt));
    }

    public enum State {Preparing, Running, Succeeded, PartiallySucceeded, Failed}

    public enum Outcome {Applied, Confirmed, Rejected, Conflict, StaleEvidence, Deferred, Unknown}

    @Entity
    public record Entry(@JsonProperty("id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericId,
                        long decisionVersion, Difference evidence, MergeDecision decision, ItemResult result,
                        boolean requested) {
        public Entry(String id, long decisionVersion, Difference evidence, MergeDecision decision, ItemResult result) {
            this(Long.valueOf(id), decisionVersion, evidence, decision, result, false);
        }

        public String id() {
            return numericId.toString();
        }

        Entry finish(ItemResult result) {
            if (this.result != null) {
                throw new MergeFailure(Reason.RoundClosed, id());
            }
            return new Entry(numericId, decisionVersion, evidence, decision, result, requested);
        }

        Entry request() {
            return requested ? this : new Entry(numericId, decisionVersion, evidence, decision, result, true);
        }
    }

    @ValueObject
    public record ItemResult(String entryId, Outcome outcome, String operation,
                             String businessRef, String checkpoint, String failure) {
        public boolean succeeded() {
            return outcome == Outcome.Applied || outcome == Outcome.Confirmed;
        }
    }

    @ValueObject
    public record Summary(long applied, long confirmed, long rejected, long conflict, long deferred,
                          long staleEvidence, long unknown) {
    }

    @ValueObject
    public record ApplyResult(State state, Summary summary, List<ItemResult> itemResults, Instant completedAt) {
        public ApplyResult {
            itemResults = List.copyOf(itemResults);
        }
    }
}
