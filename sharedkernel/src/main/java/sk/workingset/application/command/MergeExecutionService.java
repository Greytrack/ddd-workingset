package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import sk.common.error.DomainException;
import sk.workingset.application.Caller;
import sk.workingset.application.FailureReason;
import sk.workingset.application.merge.MergeComparisonService;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.domain.event.MergeAppliedEvent;
import sk.workingset.domain.event.MergeRoundSubmittedEvent;
import sk.workingset.domain.merge.MergeCommitReceipt;
import sk.workingset.domain.merge.MergeRepository;
import sk.workingset.domain.event.MergeTargetEvent;
import sk.workingset.application.command.dto.RequirementMergeCommand;
import sk.workingset.application.command.dto.RequirementMergeCommand.Operation;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison.Difference;
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
 * {@code RequirementMergeAppliedEvent} 事件在 SK 独立事务完成，二者不共用条目事务。
 * 条目失败按 Rejected/Conflict/StaleEvidence 记录，不影响已成功条目。
 */
public class MergeExecutionService {
    private static final Logger log = LoggerFactory.getLogger(MergeExecutionService.class);

    private final MergeRepository store;
    private final MergeComparisonService comparisons;
    private final RequirementWritePort writer;
    private final TrackerRepository trackers;
    private final IdentifierGenerator ids;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final MergeRelationRepairStep relationRepair;

    public MergeExecutionService(MergeRepository store, MergeComparisonService comparisons, RequirementWritePort writer,
                                 TrackerRepository trackers, IdentifierGenerator ids, TransactionTemplate transaction,
                                 ApplicationEventPublisher events, MergeRelationRepairStep relationRepair) {
        this.store = store;
        this.comparisons = comparisons;
        this.writer = writer;
        this.trackers = trackers;
        this.ids = ids;
        this.transaction = transaction;
        this.events = events;
        this.relationRepair = relationRepair;
    }

    /** 一页决策只提交 evidence + decision；条目身份由服务端在登记时生成。 */
    public record EntryRequest(Difference evidence, MergeDecision decision) {}
    @Transactional
    public MergeRound submit(WorkingSetId target, WorkingSetId source, String requestId,
                             List<EntryRequest> decisions, Caller caller) {
        java.util.Optional<MergeRound> existing = store.findRoundByRequest(requestId);
        if (existing.isPresent()) {
            return matching(existing.get(), source, target);
        }
        MergeCheckpoint.Key pair = trackerPair(decisions);
        MergeRound prepared = MergeRound.prepare(Long.toString(ids.nextId(null).longValue()), requestId, source, target, caller.id());
        MergeRound round;
        try {
            round = store.insertOrFindRound(prepared, pair.a(), pair.b()).orElseThrow(
                    () -> new MergeFailure(Reason.Conflict, requestId));
        } catch (DataIntegrityViolationException conflict) {
            // The partial unique index on active Tracker pairs is the single concurrency gate.
            // No application-level pre-check: it would add a TOCTOU window without adding safety.
            throw new MergeFailure(Reason.PairMergeInProgress, pair.a().value() + "/" + pair.b().value());
        }
        if (!round.id().equals(prepared.id())) {
            return matching(round, source, target);
        }
        registerDecisions(round, decisions);
        events.publishEvent(new MergeRoundSubmittedEvent(round.id()));
        return round(round.id(), false);
    }

    /** 一页决策必须同属一个 Tracker 分支对；返回规范化后的键。 */
    private static MergeCheckpoint.Key trackerPair(List<EntryRequest> decisions) {
        Difference evidence = decisions.getFirst().evidence();
        if (evidence == null) {
            throw new MergeFailure(Reason.InvalidDecision, "evidence is required");
        }
        MergeCheckpoint.Key pair = new MergeCheckpoint.Key(evidence.trackers().source(), evidence.trackers().target(),
                evidence.family());
        WorkingSetId sourceWorkingSet = evidence.trackers().sourceWorkingSetId();
        WorkingSetId targetWorkingSet = evidence.trackers().targetWorkingSetId();
        boolean mixed = decisions.stream().anyMatch(request -> {
            if (request.evidence() == null) {
                return true;
            }
            Difference candidate = request.evidence();
            MergeCheckpoint.Key other = new MergeCheckpoint.Key(candidate.trackers().source(),
                    candidate.trackers().target(), candidate.family());
            return !other.a().equals(pair.a()) || !other.b().equals(pair.b())
                    || !candidate.trackers().sourceWorkingSetId().equals(sourceWorkingSet)
                    || !candidate.trackers().targetWorkingSetId().equals(targetWorkingSet);
        });
        if (mixed) {
            throw new IllegalArgumentException("decisions must belong to one tracker pair");
        }
        return pair;
    }

    /** 一页决策要么整页登记成功，要么整轮回滚；失败原因决定客户端下一步动作。 */
    private void registerDecisions(MergeRound round, List<EntryRequest> requests) {
        Caller caller = Caller.of(round.caller());
        List<sk.workingset.domain.merge.Comparison.TrackerPair> candidates = comparisons.candidates(round.source(), round.target());
        MergeRound updated = round;
        for (MergeExecutionService.EntryRequest request : requests) {
            Difference frozen = request.evidence();
            if (frozen == null) {
                throw new MergeFailure(Reason.InvalidDecision, "evidence is required");
            }
            sk.workingset.domain.merge.Comparison.TrackerPair pair = frozen.trackers();
            if (!pair.sourceWorkingSetId().equals(round.source())
                    || !pair.targetWorkingSetId().equals(round.target())) {
                throw new MergeFailure(Reason.StaleEvidence, frozen.family().value());
            }
            if (!candidates.contains(pair)) {
                throw new MergeFailure(Reason.StaleEvidence, frozen.family().value());
            }
            lockEvidence(frozen);
            Difference current = comparisons.current(frozen, caller);
            if (!frozen.source().equals(current.source()) || !frozen.target().equals(current.target())) {
                throw new MergeFailure(Reason.StaleEvidence, frozen.family().value());
            }
            if (request.decision() == null) {
                throw new MergeFailure(Reason.InvalidDecision, "decision is required");
            }
            request.decision().validate(frozen);
            updated = updated.register(new Entry(Long.toString(ids.nextId(null).longValue()), store.nextDecisionVersion(),
                    frozen, request.decision(), null));
        }
        store.updateRound(updated);
    }

    private static MergeRound matching(MergeRound round, WorkingSetId source, WorkingSetId target) {
        if (!round.source().equals(source) || !round.target().equals(target)) {
            throw new MergeFailure(Reason.Conflict, "requestId is already used by another merge round");
        }
        return round;
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
            MergeRound round = round(id, false);
            if (round.result() != null) {
                return round.result();
            }
            // Keep the execution lock across entry commits. A disconnected executor releases it.
            return transaction.execute(status -> runWithExecutionLock(id));
        } catch (DataAccessException | TransactionException failure) {
            log.error("Merge round {} could not confirm execution progress", id, failure);
            throw DomainException.withMessage(FailureReason.UNKNOWN, "execution progress unavailable: " + id);
        }
    }

    private MergeRound.ApplyResult runWithExecutionLock(String id) {
        if (!store.tryLockExecution(id)) {
            MergeRound.ApplyResult result = round(id, false).result();
            if (result != null) {
                return result;
            }
            throw new MergeFailure(Reason.RoundInProgress, id);
        }

        MergeRound round = transaction.execute(status -> startExecution(id));
        for (MergeRound.Entry entry : Objects.requireNonNull(round).entries()) {
            if (entry.result() == null) {
                MergeExecutionService.Execution receipt = executeEntry(round, entry);
                transaction.executeWithoutResult(status -> recordResult(id, entry, receipt));
            }
        }
        MergeRound completed = transaction.execute(status -> {
            MergeRound current = round(id, true);
            if (current.result() != null) {
                return null;
            }
            MergeRound finished = current.complete(Instant.now());
            store.updateRound(finished);
            return finished;
        });
        if (completed != null) {
            try {
                MergeRelationRepairStep.StepResult repair = relationRepair.repair(completed);
                if (repair.failed() > 0) {
                    log.warn("Merge {} relation repair had {} failed targets", completed.id(), repair.failed());
                }
            } catch (RuntimeException failure) {
                // Relation repair is independent of Merge success; do not change the merge result.
                log.warn("Merge {} relation repair step failed", completed.id(), failure);
            }
            return completed.result();
        }
        return Objects.requireNonNull(round(id, false).result());
    }

    private MergeRound startExecution(String id) {
        MergeRound round = round(id, true);
        if (round.state() == State.Preparing) {
            round = round.start();
            store.updateRound(round);
        }
        return round;
    }

    private void recordResult(String id, Entry entry, Execution execution) {
        MergeRound round = round(id, true);
        if (recordedResult(round, entry) != null) {
            return;
        }
        MergeRound.ItemResult result = execution.result();
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
                MergeRound current = round(round.id(), true);
                MergeRound.ItemResult recorded = recordedResult(current, entry);
                if (recorded != null) {
                    return Execution.finished(recorded);
                }
                MergeCommitReceipt receipt = store.findCommitReceipt(round.id(), entry.id()).orElseGet(() -> {
                    MergeCommitReceipt committed = applyDecision(round, entry);
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
        } catch (DomainException failure) {
            FailureReason reason = FailureReason.of(failure);
            if (reason == FailureReason.UNKNOWN) {
                throw failure;
            }
            return Execution.finished(result(entry, Outcome.Rejected, operation(entry), null, null,
                    reason.name()));
        } catch (RuntimeException failure) {
            log.error("Merge entry {} could not complete", entry.id(), failure);
            throw DomainException.withMessage(FailureReason.UNKNOWN, "execution result unavailable: " + entry.id());
        }
    }

    private MergeCommitReceipt applyDecision(MergeRound round, Entry entry) {
        Difference frozen = entry.evidence();
        lockEvidence(frozen);
        if (!comparisons.candidates(round.source(), round.target()).contains(frozen.trackers())) {
            throw new MergeFailure(Reason.StaleEvidence, "tracker scope changed");
        }
        Caller caller = Caller.of(round.caller());
        Difference current = comparisons.current(frozen, caller);
        if (!frozen.source().sameExecutionShape(current.source())
                || !frozen.target().sameExecutionShape(current.target())) {
            throw new MergeFailure(Reason.StaleEvidence, "identity, revision, presence or readability changed");
        }

        String operation = operation(entry);
        Revision targetRevision = current.target().revision();
        LocalId businessRef = null;
        if (!"KEEP_TARGET".equals(operation)) {
            sk.workingset.domain.merge.Comparison.Side target = current.target();
            sk.workingset.domain.merge.Comparison.Side source = current.source();
            Map<String,Object> accepted = acceptedFields(entry, current);
            RequirementMergeCommand.Operation action = Operation.valueOf(operation);
            LocalId localId = action == Operation.CREATE
                    ? (WorkingSet.DEFAULT_ID.equals(round.target())
                            ? LocalId.of(current.family().value())
                            : LocalId.of(Long.toString(ids.nextId(null).longValue())))
                    : target.localId();
            boolean inherited = target.localId() != null && !target.localId().equals(target.contentId());
            boolean create = action == Operation.CREATE;
            RequirementMergeCommand.Result committed = writer.applyMerge(new RequirementMergeCommand(round.id(), entry.id(), round.token(), action,
                    current.trackers().target(), current.family(), localId, create ? source.revision() : target.revision(),
                    inherited || create ? null : target.lockVersion(), create ? source.version() : target.version(),
                    create ? source.fields() : target.fields(), accepted,
                    create && comparisons.isMergeBack(round.source(), round.target())));
            targetRevision = committed.revision();
            businessRef = committed.localId();
            MergeTargetEvent mergeTarget = new MergeTargetEvent(businessRef, current.family(), current.trackers().target(),
                    round.target(), targetRevision, inherited,
                    create && committed.created() && !WorkingSet.DEFAULT_ID.equals(round.target()));
            if (mergeTarget.inherited() || mergeTarget.create()) {
                events.publishEvent(new MergeAppliedEvent(mergeTarget, Instant.now()));
            }
        }
        return new MergeCommitReceipt(businessRef, current.source().revision(), targetRevision);
    }

    private ItemResult confirm(MergeRound round, Entry entry, MergeCommitReceipt receipt) {
        String operation = operation(entry);
        String businessRef = receipt.businessRef() == null ? null : receipt.businessRef().value();
        if (entry.decision().markAsMerged()) {
            sk.workingset.domain.merge.Comparison.TrackerPair trackers = entry.evidence().trackers();
            MergeCheckpoint.Key key = new MergeCheckpoint.Key(trackers.source(), trackers.target(), entry.evidence().family());
            boolean sourceIsA = key.a().equals(trackers.source());
            LocalId sourceLocal = entry.evidence().source().localId();
            LocalId targetLocal = receipt.businessRef() != null ? receipt.businessRef()
                    : entry.evidence().target().localId();
            LocalId localA = sourceIsA ? sourceLocal : targetLocal;
            LocalId localB = sourceIsA ? targetLocal : sourceLocal;
            Revision revisionA = sourceIsA ? receipt.sourceRevision() : receipt.targetRevision();
            Revision revisionB = sourceIsA ? receipt.targetRevision() : receipt.sourceRevision();
            MergeCheckpoint.Direction direction = sourceIsA ? MergeCheckpoint.Direction.A_TO_B : MergeCheckpoint.Direction.B_TO_A;
            boolean confirmed = store.recordCheckpoint(new MergeCheckpoint(key, entry.decisionVersion(),
                    revisionA, revisionB, localA, localB, round.id(), entry.evidence().kind(), direction, false,
                    round.caller()));
            return result(entry, confirmed ? Outcome.Confirmed : Outcome.Applied, operation, businessRef,
                    confirmed ? "Confirmed" : "Superseded", null);
        }
        return result(entry, Outcome.Applied, operation, businessRef, null, null);
    }

    private void lockEvidence(Difference difference) {
        Stream.of(difference.trackers().source(), difference.trackers().target()).distinct()
                .sorted(Comparator.comparing(TrackerId::value)).forEach(trackers::lockTracker);
        List<LocalId> ids = Stream.of(difference.source().localId(), difference.source().contentId(),
                        difference.target().localId(), difference.target().contentId())
                .filter(Objects::nonNull).distinct().sorted(Comparator.comparing(LocalId::value)).toList();
        writer.lockMergeEvidence(ids);
    }

    private static Map<String, Object> acceptedFields(Entry entry, Difference current) {
        LinkedHashMap<String,Object> accepted = new LinkedHashMap<String, Object>();
        entry.decision().fields().forEach((path, decision) -> {
            if (decision == FieldAction.ACCEPT) {
                Object value = current.source().fields().get(path);
                if (value == null) {
                    throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, path);
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
        return new ItemResult(entry.id(), outcome, operation, businessRef, checkpoint, failure);
    }

    private MergeRound round(String id, boolean lock) {
        return store.findRound(id, lock).orElseThrow(() -> new MergeFailure(Reason.RoundNotFound, id));
    }
}
