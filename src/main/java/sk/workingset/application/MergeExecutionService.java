package sk.workingset.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.application.event.RequirementMergeApplied;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.MergeCommitReceipt;
import sk.workingset.application.port.MergeStore;
import sk.workingset.application.port.MergeTarget;
import sk.workingset.application.port.RequirementMergeCommand;
import sk.workingset.application.port.RequirementMergeCommand.Operation;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeDecision;
import sk.workingset.domain.merge.MergeDecision.FieldAction;
import sk.workingset.domain.merge.MergeFailure;
import sk.workingset.domain.merge.MergeFailure.Reason;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeRound.Entry;
import sk.workingset.domain.merge.MergeRound.ItemResult;
import sk.workingset.domain.merge.MergeRound.Outcome;
import sk.workingset.domain.merge.MergeRound.State;

/**
 * Merge 轮次执行：逐条应用决定、记录结果与终态。
 *
 * <p>D15-B：条目业务 apply 在独立事务提交；目标变体切换/创建由
 * {@code RequirementMergeApplied} 事件在 SK 独立事务完成，二者不共用条目事务。
 * 条目失败按 Rejected/Conflict/StaleEvidence 记录，不影响已成功条目。
 */
public class MergeExecutionService {
    private static final Logger log = LoggerFactory.getLogger(MergeExecutionService.class);

    private final MergeStore store;
    private final MergeComparisonService comparisons;
    private final RequirementWritePort writer;
    private final TrackerRepository trackers;
    private final IdentifierGenerator ids;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    public MergeExecutionService(MergeStore store, MergeComparisonService comparisons, RequirementWritePort writer,
                                 TrackerRepository trackers, IdentifierGenerator ids, TransactionTemplate transaction,
                                 ApplicationEventPublisher events) {
        this.store = store;
        this.comparisons = comparisons;
        this.writer = writer;
        this.trackers = trackers;
        this.ids = ids;
        this.transaction = transaction;
        this.events = events;
    }

    public record EntryRequest(String entryKey, MergeDecision decision) {}
    public record Registration(String entryKey, String entryId, Reason failure, String detail) {}

    @Transactional
    public MergeRound start(WorkingSetId target, String token, Caller caller) {
        var comparison = comparisons.find(token);
        if (!target.equals(comparison.target())) {
            throw new MergeFailure(Reason.MergeNotAllowed, "comparison target differs");
        }
        var candidates = comparisons.candidates(comparison.source(), target);
        if (!candidates.containsAll(comparison.candidates())) {
            throw new MergeFailure(Reason.MergeNotAllowed, "comparison scope has changed");
        }
        var round = MergeRound.prepare(UUID.randomUUID().toString(), comparison, caller.id());
        if (!store.insertRound(round)) {
            throw new MergeFailure(Reason.ComparisonAlreadyUsed, token);
        }
        return round;
    }

    @Transactional
    public List<Registration> register(String roundId, List<EntryRequest> requests) {
        var round = round(roundId, true);
        if (round.state() != State.Preparing) {
            throw new MergeFailure(Reason.RoundClosed, roundId);
        }
        var comparison = comparisons.find(round.token());
        var results = new ArrayList<Registration>();
        var caller = Caller.of(round.caller());
        for (var request : requests) {
            try {
                var duplicate = round.entries().stream()
                        .filter(entry -> entry.evidence().key().equals(request.entryKey())).findFirst();
                if (duplicate.isPresent()) {
                    results.add(new Registration(request.entryKey(), duplicate.get().id(), null, null));
                    continue;
                }
                var frozen = comparison.differences().stream().filter(diff -> diff.key().equals(request.entryKey()))
                        .findFirst().orElseThrow(() -> new MergeFailure(Reason.InvalidDecision, request.entryKey()));
                lockEvidence(frozen);
                var current = comparisons.current(frozen, caller);
                if (!frozen.source().equals(current.source()) || !frozen.target().equals(current.target())
                        || !comparisons.candidates(round.source(), round.target()).contains(frozen.trackers())) {
                    throw new MergeFailure(Reason.StaleEvidence, request.entryKey());
                }
                if (request.decision() == null) {
                    throw new MergeFailure(Reason.InvalidDecision, "decision is required");
                }
                request.decision().validate(frozen);
                String entryId = UUID.randomUUID().toString();
                round = round.register(new Entry(entryId, store.nextDecisionVersion(), frozen, request.decision(), null));
                results.add(new Registration(request.entryKey(), entryId, null, null));
            } catch (MergeFailure failure) {
                results.add(new Registration(request.entryKey(), null, failure.reason(), failure.getMessage()));
            }
        }
        store.updateRound(round);
        return List.copyOf(results);
    }

    public MergeRound get(String id) { return round(id, false); }

    @Transactional
    public void revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion) {
        if (!store.revokeCheckpoint(key, expectedVersion, store.nextDecisionVersion())) {
            throw new MergeFailure(Reason.Conflict, "checkpoint is missing, superseded or already revoked");
        }
    }

    public MergeRound.ApplyResult apply(String id) {
        try {
            var round = round(id, false);
            if (round.result() != null) {
                return round.result();
            }
            // Keep the execution lock across entry commits. A disconnected executor releases it.
            return transaction.execute(status -> runWithExecutionLock(id));
        } catch (DataAccessException | TransactionException failure) {
            log.error("Merge round {} could not confirm execution progress", id, failure);
            throw new ApplicationFailure(FailureReason.UNKNOWN, "execution progress unavailable: " + id);
        }
    }

    private MergeRound.ApplyResult runWithExecutionLock(String id) {
        if (!store.tryLockExecution(id)) {
            var result = round(id, false).result();
            if (result != null) {
                return result;
            }
            throw new MergeFailure(Reason.RoundInProgress, id);
        }
        if (!store.supportsCommitRecovery(id)) {
            throw new ApplicationFailure(FailureReason.UNKNOWN, "legacy round requires reconciliation: " + id);
        }

        var round = transaction.execute(status -> startExecution(id));
        for (var entry : Objects.requireNonNull(round).entries()) {
            if (entry.result() == null) {
                var receipt = executeEntry(round, entry);
                transaction.executeWithoutResult(status -> recordResult(id, entry, receipt));
            }
        }
        return transaction.execute(status -> {
            var current = round(id, true);
            if (current.result() != null) {
                return current.result();
            }
            var completed = current.complete(Instant.now());
            store.updateRound(completed);
            return completed.result();
        });
    }

    private MergeRound startExecution(String id) {
        var round = round(id, true);
        if (round.state() == State.Preparing) {
            round = round.start();
            store.updateRound(round);
        }
        return round;
    }

    private void recordResult(String id, Entry entry, Execution execution) {
        var round = round(id, true);
        if (recordedResult(round, entry) != null) {
            return;
        }
        var result = execution.result();
        if (execution.receipt() != null) {
            result = confirm(round, entry, execution.receipt());
        }
        store.updateRound(round.finishEntry(entry, result));
    }

    private static ItemResult recordedResult(MergeRound round, Entry entry) {
        return round.entries().stream().filter(current -> current.id().equals(entry.id()))
                .findFirst().orElseThrow().result();
    }

    private record Execution(ItemResult result, MergeCommitReceipt receipt) {
        static Execution finished(ItemResult result) {
            return new Execution(result, null);
        }

        static Execution committed(MergeCommitReceipt receipt) {
            return new Execution(null, receipt);
        }
    }

    private Execution executeEntry(MergeRound round, Entry entry) {
        if (entry.decision().deferred()) {
            return Execution.finished(result(entry, Outcome.Deferred, "DEFER", null, null, null));
        }
        try {
            // The receipt and any event publication commit with the ALM write.
            return transaction.execute(status -> {
                // Also serialize receipt lookup and writing if a disconnected executor is still unwinding.
                var current = round(round.id(), true);
                var recorded = recordedResult(current, entry);
                if (recorded != null) {
                    return Execution.finished(recorded);
                }
                var receipt = store.findCommitReceipt(round.id(), entry.id()).orElseGet(() -> {
                    var committed = applyDecision(round, entry);
                    store.insertCommitReceipt(round.id(), entry.id(), committed);
                    return committed;
                });
                return Execution.committed(receipt);
            });
        } catch (MergeFailure failure) {
            Outcome outcome = switch (failure.reason()) {
                case StaleEvidence, MergeNotAllowed -> Outcome.StaleEvidence;
                case Conflict -> Outcome.Conflict;
                default -> Outcome.Rejected;
            };
            return Execution.finished(result(entry, outcome, operation(entry), null, null, failure.getMessage()));
        } catch (DataIntegrityViolationException failure) {
            return Execution.finished(result(entry, Outcome.Conflict, operation(entry), null, null,
                    "target persistence conflict"));
        } catch (ApplicationFailure failure) {
            if (failure.reason() == FailureReason.UNKNOWN) {
                throw failure;
            }
            return Execution.finished(result(entry, Outcome.Rejected, operation(entry), null, null,
                    failure.reason().name()));
        } catch (RuntimeException failure) {
            log.error("Merge entry {} could not complete", entry.id(), failure);
            throw new ApplicationFailure(FailureReason.UNKNOWN, "execution result unavailable: " + entry.id());
        }
    }

    private MergeCommitReceipt applyDecision(MergeRound round, Entry entry) {
        var frozen = entry.evidence();
        lockEvidence(frozen);
        if (!comparisons.candidates(round.source(), round.target()).contains(frozen.trackers())) {
            throw new MergeFailure(Reason.StaleEvidence, "tracker scope changed");
        }
        var caller = Caller.of(round.caller());
        var current = comparisons.current(frozen, caller);
        if (!frozen.source().sameExecutionShape(current.source())
                || !frozen.target().sameExecutionShape(current.target())) {
            throw new MergeFailure(Reason.StaleEvidence, "identity, revision, presence or readability changed");
        }

        String operation = operation(entry);
        Revision targetRevision = current.target().revision();
        LocalId businessRef = null;
        if (!"KEEP_TARGET".equals(operation)) {
            var target = current.target();
            var source = current.source();
            var accepted = acceptedFields(entry, current);
            var action = Operation.valueOf(operation);
            LocalId localId = action == Operation.CREATE ? LocalId.of(Long.toString(ids.nextId())) : target.localId();
            boolean inherited = target.localId() != null && !target.localId().equals(target.contentId());
            boolean create = action == Operation.CREATE;
            comparisons.assertWriteCapability(current, create, caller);
            targetRevision = writer.applyMerge(new RequirementMergeCommand(round.id(), entry.id(), round.token(), action,
                    current.trackers().target(), current.family(), localId, create ? source.revision() : target.revision(),
                    inherited || create ? null : target.lockVersion(), create ? source.version() : target.version(),
                    create ? source.fields() : target.fields(), accepted));
            var mergeTarget = new MergeTarget(localId, current.family(), current.trackers().target(),
                    round.target(), targetRevision, inherited,
                    create && !WorkingSet.DEFAULT_ID.equals(round.target()));
            if (mergeTarget.inherited() || mergeTarget.create()) {
                events.publishEvent(new RequirementMergeApplied(mergeTarget, Instant.now()));
            }
            businessRef = localId;
        }
        return new MergeCommitReceipt(businessRef, current.source().revision(), targetRevision);
    }

    private ItemResult confirm(MergeRound round, Entry entry, MergeCommitReceipt receipt) {
        String operation = operation(entry);
        String businessRef = receipt.businessRef() == null ? null : receipt.businessRef().value();
        if (entry.decision().markAsMerged()) {
            var key = new MergeCheckpoint.Key(round.source(), round.target(), entry.evidence().family());
            boolean confirmed = store.recordCheckpoint(new MergeCheckpoint(key, entry.decisionVersion(),
                    receipt.sourceRevision(), receipt.targetRevision(), round.token(), false));
            return result(entry, confirmed ? Outcome.Confirmed : Outcome.Applied, operation, businessRef,
                    confirmed ? "Confirmed" : "Superseded", null);
        }
        return result(entry, Outcome.Applied, operation, businessRef, null, null);
    }

    private void lockEvidence(Difference difference) {
        Stream.of(difference.trackers().source(), difference.trackers().target()).distinct()
                .sorted(Comparator.comparing(TrackerId::value)).forEach(trackers::lockTracker);
        var ids = Stream.of(difference.source().localId(), difference.source().contentId(),
                        difference.target().localId(), difference.target().contentId())
                .filter(Objects::nonNull).distinct().sorted(Comparator.comparing(LocalId::value)).toList();
        writer.lockMergeEvidence(ids);
    }

    private static Map<String, Object> acceptedFields(Entry entry, Difference current) {
        var accepted = new LinkedHashMap<String, Object>();
        entry.decision().fields().forEach((path, decision) -> {
            if (decision == FieldAction.ACCEPT) {
                var value = current.source().fields().get(path);
                if (value == null) {
                    throw new ApplicationFailure(FailureReason.HISTORY_UNAVAILABLE, path);
                }
                accepted.put(path, value);
            }
        });
        return Map.copyOf(accepted);
    }

    private static String operation(Entry entry) {
        if (entry.decision().existence() != null) {
            return entry.decision().existence().name();
        }
        return entry.decision().fields().containsValue(FieldAction.ACCEPT) ? "UPDATE" : "KEEP_TARGET";
    }

    private static ItemResult result(Entry entry, Outcome outcome, String operation, String businessRef,
                                     String checkpoint, String failure) {
        return new ItemResult(entry.id(), entry.evidence().key(), outcome, operation, businessRef, checkpoint, failure);
    }

    private MergeRound round(String id, boolean lock) {
        return store.findRound(id, lock).orElseThrow(() -> new MergeFailure(Reason.RoundNotFound, id));
    }
}
