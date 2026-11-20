package sk.workingset.domain.merge;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.ValueObject;
import sk.common.error.DomainException;
import sk.workingset.domain.Revision;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.MergeResCode;

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
            throw DomainException.withMessage(MergeResCode.ROUND_CLOSED, id());
        }
        if (entries.stream().anyMatch(existing -> existing.evidence().family().equals(entry.evidence().family()))) {
            throw DomainException.withMessage(MergeResCode.INVALID_DECISION, "duplicate item family in one round");
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
        return finishEntry(entry.id(), itemResult, null);
    }

    public MergeRound finishApplied(String entryId, String operation, MergeCommitReceipt receipt) {
        Entry entry = entries.stream().filter(candidate -> candidate.id().equals(entryId)).findFirst()
                .orElseThrow(() -> DomainException.withMessage(MergeResCode.ROUND_NOT_FOUND, entryId));
        boolean confirm = entry.decision().confirmsOnSuccess(entry.evidence());
        String businessRef = receipt.businessRef() == null ? null : receipt.businessRef().value();
        ItemResult itemResult = new ItemResult(entryId, confirm ? Outcome.Confirmed : Outcome.Applied,
                operation, businessRef, confirm ? "Confirmed" : null, null);
        return finishEntry(entryId, itemResult,
                confirm ? new Confirmation(entry.evidence().source().revision(), receipt.targetRevision()) : null);
    }

    private MergeRound finishEntry(String entryId, ItemResult itemResult, Confirmation confirmation) {
        if (state != State.Running) {
            throw DomainException.withMessage(MergeResCode.ROUND_CLOSED, id());
        }
        List<Entry> updated = entries.stream().map(candidate -> candidate.id().equals(entryId)
                ? candidate.finish(itemResult, confirmation) : candidate).toList();
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
        if (state != State.Running || awaitingResults()) {
            throw DomainException.withMessage(MergeResCode.ROUND_CLOSED, id());
        }
        List<MergeRound.ItemResult> results = entries.stream().map(Entry::result).toList();
        long successful = results.stream().filter(ItemResult::succeeded).count();
        State terminal = successful == 0 ? State.Failed
                : successful == results.size() ? State.Succeeded : State.PartiallySucceeded;
        MergeRound.Summary summary = new Summary(count(results, Outcome.Applied), count(results, Outcome.Confirmed),
                count(results, Outcome.Rejected),
                count(results, Outcome.Conflict), count(results, Outcome.StaleEvidence),
                count(results, Outcome.Unknown));
        return new MergeRound(numericId, token, requestId, source, target, caller, terminal, entries,
                new ApplyResult(terminal, summary, results, completedAt));
    }

    public enum State {Preparing, Running, Succeeded, PartiallySucceeded, Failed}

    public enum Outcome {Applied, Confirmed, Rejected, Conflict, StaleEvidence, Unknown}

    @Entity
    public record Entry(@JsonProperty("id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericId,
                        Difference evidence, MergeDecision decision, ItemResult result,
                        boolean requested, Confirmation confirmation) {
        public Entry(String id, Difference evidence, MergeDecision decision, ItemResult result) {
            this(Long.valueOf(id), evidence, decision, result, false, null);
        }

        public String id() {
            return numericId.toString();
        }

        Entry finish(ItemResult result, Confirmation confirmation) {
            if (this.result != null) {
                throw DomainException.withMessage(MergeResCode.ROUND_CLOSED, id());
            }
            boolean required = result.succeeded() && decision.confirmsOnSuccess(evidence);
            if (required != (confirmation != null) || required != (result.outcome() == Outcome.Confirmed)) {
                throw DomainException.withMessage(MergeResCode.INVALID_DECISION, "confirmation must match the applied entry");
            }
            return new Entry(numericId, evidence, decision, result, requested, confirmation);
        }

        Entry request() {
            return requested ? this : new Entry(numericId, evidence, decision, result, true, confirmation);
        }
    }

    /**
     * Own business revisions of the compared variants, not the revisions used to read inherited content.
     */
    @ValueObject
    public record Confirmation(Revision sourceRevision, Revision targetRevision) {
        public Confirmation {
            Objects.requireNonNull(sourceRevision, "source variant revision");
            Objects.requireNonNull(targetRevision, "target variant revision");
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
    public record Summary(long applied, long confirmed, long rejected, long conflict,
                          long staleEvidence, long unknown) {
    }

    @ValueObject
    public record ApplyResult(State state, Summary summary, List<ItemResult> itemResults, Instant completedAt) {
        public ApplyResult {
            itemResults = List.copyOf(itemResults);
        }
    }
}
