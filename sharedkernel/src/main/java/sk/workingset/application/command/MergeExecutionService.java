package sk.workingset.application.command;

import sk.workingset.error.MergeResCode;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.application.merge.MergeComparisonService;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.event.*;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Side;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.*;
import sk.workingset.domain.merge.MergeDecision.FieldAction;
import sk.workingset.domain.merge.MergeRound.Entry;
import sk.workingset.domain.merge.MergeRound.ItemResult;
import sk.workingset.domain.merge.MergeRound.Outcome;
import sk.workingset.domain.merge.MergeRound.State;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge 轮次执行：登记决定、发布逐条应用事件、消费业务结果并收敛轮次。
 *
 * <p>业务 apply 不再由 SK 同步调用。SK 发布 {@link MergeEntryApprovedEvent}，ALM 在
 * 自己的事务中应用并发布 {@link MergeEntryResolvedEvent}，SK 监听结果后由 MergeRound 决定是否确认，
 * 由轮次仓储在同一事务记录确认成员、条目结果及轮次状态。LTI 切换仍由 {@link MergeAppliedEvent} 异步执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergeExecutionService {

    private final MergeRepository store;
    private final MergeComparisonService comparisons;
    private final IdentifierGenerator ids;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final MergeRelationRepairStep relationRepair;

    private static TrackerPair trackerPair(List<EntryRequest> decisions) {
        Difference evidence = decisions.getFirst().evidence();
        if (evidence == null) {
            throw DomainException.withMessage(MergeResCode.INVALID_DECISION, "evidence is required");
        }
        TrackerPair pair = evidence.trackers();
        if (decisions.stream().anyMatch(request -> request.evidence() == null
                || !pair.equals(request.evidence().trackers()))) {
            throw new IllegalArgumentException("decisions must belong to one tracker pair");
        }
        return pair;
    }

    private static MergeRound matching(MergeRound round, WorkingSetId source, WorkingSetId target) {
        if (!round.source().equals(source) || !round.target().equals(target)) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "requestId is already used by another merge round");
        }
        return round;
    }

    private static MergeRound finish(MergeRound round, Entry entry, ItemResult result) {
        return round.finishEntry(entry, result);
    }

    private static Outcome outcome(MergeEntryResolvedEvent.Outcome outcome) {
        return switch (outcome) {
            case APPLIED -> Outcome.Applied;
            case CONFLICT -> Outcome.Conflict;
            case STALE_EVIDENCE -> Outcome.StaleEvidence;
            case UNKNOWN -> Outcome.Unknown;
            case REJECTED -> Outcome.Rejected;
        };
    }

    private static Map<String, Object> acceptedFields(Entry entry, Difference current) {
        LinkedHashMap<String, Object> accepted = new LinkedHashMap<>();
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
        return switch (entry.evidence().kind()) {
            case MODIFIED -> entry.decision().fields().containsValue(FieldAction.ACCEPT) ? "UPDATE" : "IGNORED";
            case ADDED -> entry.decision().existence() != MergeDecision.ExistenceAction.ACCEPT ? "IGNORED"
                    : entry.evidence().target().presence() == Comparison.Presence.DELETED ? "RESTORE" : "CREATE";
            case DELETED -> entry.decision().existence() == MergeDecision.ExistenceAction.ACCEPT ? "DELETE" : "IGNORED";
        };
    }

    private static ItemResult result(Entry entry, Outcome outcome, String operation, String businessRef,
                                     String checkpoint, String failure) {
        return new ItemResult(entry.id(), outcome, operation, businessRef, checkpoint, failure);
    }

    @Transactional
    public MergeRound submit(WorkingSetId target, WorkingSetId source, String requestId,
                             List<EntryRequest> decisions, String caller) {
        java.util.Optional<MergeRound> existing = store.findByRequestId(requestId);
        if (existing.isPresent()) {
            return matching(existing.get(), source, target);
        }
        TrackerPair pair = trackerPair(decisions);
        MergeRound prepared = MergeRound.prepare(Long.toString(ids.nextId(null).longValue()), requestId, source, target, caller);
        // 先占幂等键：并发提交同一 requestId 时，save 保留先到轮次，后到请求直接返回该轮次。
        store.save(prepared);
        MergeRound winner = store.findByRequestId(requestId)
                .orElseThrow(() -> DomainException.withMessage(MergeResCode.CONFLICT, requestId));
        if (!winner.id().equals(prepared.id())) {
            return matching(winner, source, target);
        }
        try {
            registerDecisions(prepared, decisions);
        } catch (DataIntegrityViolationException conflict) {
            throw DomainException.withMessage(MergeResCode.PAIR_MERGE_IN_PROGRESS, pair.source().value() + "/" + pair.target().value());
        }
        events.publishEvent(new MergeRoundSubmittedEvent(prepared.id()));
        return round(prepared.id());
    }

    /**
     * 一页决策要么整页登记成功，要么整轮回滚。
     */
    private MergeRound registerDecisions(MergeRound round, List<EntryRequest> requests) {
        List<sk.workingset.domain.merge.Comparison.TrackerPair> candidates = comparisons.candidates(round.source(), round.target());
        MergeRound updated = round;
        for (EntryRequest request : requests) {
            Difference frozen = request.evidence();
            if (frozen == null) {
                throw DomainException.withMessage(MergeResCode.INVALID_DECISION, "evidence is required");
            }
            sk.workingset.domain.merge.Comparison.TrackerPair pair = frozen.trackers();
            if (!pair.sourceWorkingSetId().equals(round.source())
                    || !pair.targetWorkingSetId().equals(round.target())) {
                throw DomainException.withMessage(MergeResCode.STALE_EVIDENCE, frozen.family().value());
            }
            if (!candidates.contains(pair)) {
                throw DomainException.withMessage(MergeResCode.STALE_EVIDENCE, frozen.family().value());
            }
            Difference current = comparisons.current(frozen);
            if (!frozen.source().equals(current.source()) || !frozen.target().equals(current.target())) {
                throw DomainException.withMessage(MergeResCode.STALE_EVIDENCE, frozen.family().value());
            }
            if (request.decision() == null) {
                throw DomainException.withMessage(MergeResCode.INVALID_DECISION, "decision is required");
            }
            request.decision().validate(frozen);
            updated = updated.register(new Entry(Long.toString(ids.nextId(null).longValue()),
                    frozen, request.decision(), null));
        }
        store.save(updated);
        return updated;
    }

    public MergeRound get(String id) {
        return round(id);
    }

    /**
     * 异步启动：发布尚未请求的条目事件；返回当前轮次供客户端轮询。
     */
    public MergeRound apply(String id) {
        try {
            return transaction.execute(status -> dispatch(id));
        } catch (DataAccessException | TransactionException failure) {
            log.error("Merge round {} could not dispatch apply requests", id, failure);
            throw DomainException.withMessage(FailureReason.UNKNOWN, "execution progress unavailable: " + id);
        }
    }

    private MergeRound dispatch(String id) {
        MergeRound round = round(id);
        if (round.result() != null) {
            return round;
        }
        if (round.state() == State.Preparing) {
            round = round.start();
        }
        for (Entry entry : round.entries()) {
            if (entry.result() != null || entry.requested()) {
                continue;
            }
            Difference current;
            try {
                current = comparisons.current(entry.evidence());
                verifyCurrent(entry, current);
            } catch (DomainException failure) {
                if (failure.getResCode() != MergeResCode.STALE_EVIDENCE) {
                    throw failure;
                }
                round = finish(round, entry,
                        result(entry, Outcome.StaleEvidence, operation(entry), null, null, failure.getMessage()));
                continue;
            }
            String operation = operation(entry);
            if ("IGNORED".equals(operation)) {
                MergeCommitReceipt receipt = new MergeCommitReceipt(null,
                        current.source().revision(), current.target().revision());
                round = round.finishApplied(entry.id(), operation, receipt);
                continue;
            }
            events.publishEvent(approve(round, entry, current, operation));
            round = round.request(entry.id());
        }
        completeIfReady(round);
        return round(id);
    }

    private MergeEntryApprovedEvent approve(MergeRound round, Entry entry, Difference current, String operation) {
        Side target = current.target();
        Side source = current.source();
        MergeEntryApprovedEvent.Operation action = MergeEntryApprovedEvent.Operation.valueOf(operation);
        LocalId localId = action == MergeEntryApprovedEvent.Operation.CREATE
                ? (WorkingSet.DEFAULT_ID.equals(round.target())
                ? LocalId.of(current.family().value())
                : LocalId.of(Long.toString(ids.nextId(null).longValue())))
                : target.localId();
        boolean inherited = target.localId() != null && !target.localId().equals(target.contentId());
        boolean create = action == MergeEntryApprovedEvent.Operation.CREATE;
        Revision sourceRevision = source.revision() != null ? source.revision() : Revision.of(0);
        Revision targetRevision = create ? sourceRevision
                : target.revision() != null ? target.revision() : Revision.of(0);
        return new MergeEntryApprovedEvent(round.id(), entry.id(), round.token(), action,
                current.trackers().target(), current.family(), localId,
                sourceRevision, targetRevision,
                inherited || create ? null : target.lockVersion(),
                create ? source.version() : target.version(),
                create || action == MergeEntryApprovedEvent.Operation.RESTORE ? source.fields() : target.fields(),
                acceptedFields(entry, current), inherited);
    }

    /**
     * 消费 ALM 的条目结果；同一结果重复到达时幂等。
     */
    public MergeRound.ApplyResult recordResult(MergeEntryResolvedEvent event) {
        try {
            return transaction.execute(status -> resolve(event));
        } catch (DataAccessException | TransactionException failure) {
            log.error("Merge round {} could not record entry {}", event.roundId(), event.entryId(), failure);
            throw DomainException.withMessage(FailureReason.UNKNOWN, "execution result unavailable: " + event.entryId());
        }
    }

    private MergeRound.ApplyResult resolve(MergeEntryResolvedEvent event) {
        MergeRound round = round(event.roundId());
        Entry entry = round.entries().stream().filter(candidate -> candidate.id().equals(event.entryId()))
                .findFirst().orElseThrow(() -> DomainException.withMessage(MergeResCode.ROUND_NOT_FOUND, event.entryId()));
        if (entry.result() != null) {
            return round.result();
        }
        if (event.outcome() == MergeEntryResolvedEvent.Outcome.APPLIED) {
            MergeCommitReceipt receipt = new MergeCommitReceipt(event.businessRef(),
                    event.sourceRevision(), event.targetRevision());
            round = round.finishApplied(entry.id(), operation(entry), receipt);
            MergeTargetEvent mergeTarget = new MergeTargetEvent(event.businessRef(), entry.evidence().family(),
                    entry.evidence().trackers().target(), round.target(), event.targetRevision(),
                    event.inherited(), event.created() && !WorkingSet.DEFAULT_ID.equals(round.target()));
            if (event.inherited() || event.created()) {
                events.publishEvent(new MergeAppliedEvent(mergeTarget, Instant.now()));
            }
        } else {
            round = finish(round, entry, result(entry, outcome(event.outcome()), operation(entry), null,
                    null, event.failure()));
        }
        return completeIfReady(round);
    }

    private MergeRound.ApplyResult completeIfReady(MergeRound round) {
        if (round.awaitingResults()) {
            store.save(round);
            return null;
        }
        MergeRound finished = round.complete(Instant.now());
        store.save(finished);
        try {
            MergeRelationRepairStep.StepResult repair = relationRepair.repair(finished);
            if (repair.failed() > 0) {
                log.warn("Merge {} relation repair had {} failed targets", finished.id(), repair.failed());
            }
        } catch (RuntimeException failure) {
            log.warn("Merge {} relation repair step failed", finished.id(), failure);
        }
        return finished.result();
    }

    private void verifyCurrent(Entry entry, Difference current) {
        if (!entry.evidence().source().sameExecutionShape(current.source())
                || !entry.evidence().target().sameExecutionShape(current.target())) {
            throw DomainException.withMessage(MergeResCode.STALE_EVIDENCE, "identity, revision, presence or readability changed");
        }
    }

    private MergeRound round(String id) {
        return store.findById(id).orElseThrow(() -> DomainException.withMessage(MergeResCode.ROUND_NOT_FOUND, id));
    }

    /**
     * 一页决策只提交 evidence + decision；条目身份由服务端在登记时生成。
     */
    public record EntryRequest(Difference evidence, sk.workingset.domain.merge.MergeDecision decision) {
    }
}
