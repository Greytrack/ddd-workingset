package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.MergeEvidenceReader;
import com.airedgesoft.sharedkernel.workingset.domain.event.*;
import com.airedgesoft.sharedkernel.workingset.domain.merge.*;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Difference;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Side;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.TrackerPair;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeDecision.FieldAction;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.Entry;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.ItemResult;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.Outcome;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.State;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.error.MergeResCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private final MergeRepository mergeRepository;
    private final MergeEvidenceReader evidence;
    private final TrackerRepository trackers;
        private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final MergeRelationRepairStep relationRepair;

    private static TrackerPair trackerPair(List<EntryRequest> decisions) {
        Difference evidence = decisions.getFirst().evidence();
        if (evidence == null) {
            throw new DomainException(MergeResCode.INVALID_DECISION, "evidence is required");
        }
        TrackerPair pair = evidence.trackers();
        if (decisions.stream().anyMatch(request -> request.evidence() == null
                || !pair.equals(request.evidence().trackers()))) {
            throw new IllegalArgumentException("decisions must belong to one tracker pair");
        }
        return pair;
    }

    private static MergeRound matching(MergeRound round, Long source, Long target) {
        if (!round.source().equals(source) || !round.target().equals(target)) {
            throw new DomainException(MergeResCode.CONFLICT, "requestId is already used by another merge round");
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

    private static Map<String, Object> acceptedFields(Entry entry) {
        LinkedHashMap<String, Object> accepted = new LinkedHashMap<>();
        entry.decision().fields().forEach((path, decision) -> {
            if (decision == FieldAction.ACCEPT) {
                Object value = entry.evidence().source().fields().get(path);
                if (value == null) {
                    throw new DomainException(FailureReason.HISTORY_UNAVAILABLE, path);
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

    /**
     * 按 requestId 幂等登记一页合并决策并发布提交事件；同键返回原轮次，换方向返回冲突。
     */
    @Transactional
    public MergeRound submit(Long target, Long source, String requestId,
                             List<EntryRequest> decisions, String caller) {
        Optional<MergeRound> existing = mergeRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return matching(existing.get(), source, target);
        }
        TrackerPair pair = trackerPair(decisions);
        MergeRound prepared = MergeRound.prepare(Long.toString(Ids.nextId()), requestId, source, target, caller);
        // 原子认领幂等键：同一 requestId 的并发提交中只有一个插入成功，其余读取 winner。
        MergeRound winner = mergeRepository.claim(prepared)
                ? prepared
                : mergeRepository.findByRequestId(requestId)
                .orElseThrow(() -> new DomainException(MergeResCode.CONFLICT, requestId));
        if (!winner.id().equals(prepared.id())) {
            return matching(winner, source, target);
        }
        try {
            registerDecisions(prepared, decisions);
        } catch (DataIntegrityViolationException conflict) {
            throw new DomainException(MergeResCode.PAIR_MERGE_IN_PROGRESS, Long.toString(pair.source()) + "/" + Long.toString(pair.target()));
        }
        events.publishEvent(new MergeRoundSubmittedEvent(prepared.id()));
        return round(prepared.id());
    }

    /**
     * 一页决策要么整页登记成功，要么整轮回滚。
     */
    private MergeRound registerDecisions(MergeRound round, List<EntryRequest> requests) {
        List<Comparison.TrackerPair> candidates = evidence.candidates(round.source(), round.target());
        MergeRound updated = round;
        for (EntryRequest request : requests) {
            Difference frozen = request.evidence();
            if (frozen == null) {
                throw new DomainException(MergeResCode.INVALID_DECISION, "evidence is required");
            }
            Comparison.TrackerPair pair = frozen.trackers();
            if (!pair.sourceWorkingSetId().equals(round.source())
                    || !pair.targetWorkingSetId().equals(round.target())) {
                throw new DomainException(MergeResCode.STALE_EVIDENCE, Long.toString(frozen.family()));
            }
            if (!candidates.contains(pair)) {
                throw new DomainException(MergeResCode.STALE_EVIDENCE, Long.toString(frozen.family()));
            }
            Difference current = evidence.current(frozen);
            if (!frozen.source().equals(current.source()) || !frozen.target().equals(current.target())) {
                throw new DomainException(MergeResCode.STALE_EVIDENCE, Long.toString(frozen.family()));
            }
            if (request.decision() == null) {
                throw new DomainException(MergeResCode.INVALID_DECISION, "decision is required");
            }
            request.decision().validate(frozen);
            updated = updated.register(new Entry(Long.toString(Ids.nextId()),
                    frozen, request.decision(), null));
        }
        mergeRepository.save(updated);
        return updated;
    }

    /**
     * 按 id 读取轮次。
     */
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
            throw new DomainException(FailureReason.UNKNOWN, "execution progress unavailable: " + id);
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
            Difference frozen = entry.evidence();
            String operation = operation(entry);
            if ("IGNORED".equals(operation)) {
                MergeCommitReceipt receipt = new MergeCommitReceipt(null,
                        frozen.source().revision(), frozen.target().revision());
                round = round.finishApplied(entry.id(), operation, receipt);
                continue;
            }
            Optional<Tracker> targetTracker = trackers.findById(frozen.trackers().target());
            if (targetTracker.isEmpty()) {
                round = finish(round, entry, result(entry, Outcome.Conflict, operation, null, null,
                        "target tracker no longer exists"));
                continue;
            }
            MergeEntryApprovedEvent approved;
            try {
                approved = approve(round, entry, targetTracker.orElseThrow(), operation);
            } catch (DomainException failure) {
                if (FailureReason.of(failure) != FailureReason.HISTORY_UNAVAILABLE) {
                    throw failure;
                }
                round = finish(round, entry, result(entry, Outcome.Rejected, operation, null, null, failure.getMessage()));
                continue;
            }
            events.publishEvent(approved);
            round = round.request(entry.id());
        }
        completeIfReady(round);
        return round(id);
    }

    private MergeEntryApprovedEvent approve(MergeRound round, Entry entry, Tracker tracker, String operation) {
        Difference frozen = entry.evidence();
        Side target = frozen.target();
        Side source = frozen.source();
        MergeEntryApprovedEvent.Operation action = MergeEntryApprovedEvent.Operation.valueOf(operation);
        Long localId = action == MergeEntryApprovedEvent.Operation.CREATE
                ? (WorkingSet.DEFAULT_ID.equals(round.target())
                ? frozen.family()
                : Ids.nextId())
                : target.localId();
        boolean inherited = target.localId() != null && !target.localId().equals(target.contentId());
        boolean create = action == MergeEntryApprovedEvent.Operation.CREATE;
        Integer sourceRevision = source.revision() != null ? source.revision() : 0;
        Integer targetRevision = create ? sourceRevision
                : target.revision() != null ? target.revision() : 0;
        return new MergeEntryApprovedEvent(round.id(), entry.id(), round.token(), action,
                tracker.trackerId(), frozen.family(), localId,
                sourceRevision, targetRevision,
                inherited || create ? null : target.lockVersion(),
                create ? source.version() : target.version(),
                create || action == MergeEntryApprovedEvent.Operation.RESTORE ? source.fields() : target.fields(),
                acceptedFields(entry), inherited, tracker.numericClassificationId());
    }

    /**
     * 消费 ALM 的条目结果；同一结果重复到达时幂等。
     */
    public MergeRound.ApplyResult recordResult(MergeEntryResolvedEvent event) {
        try {
            return transaction.execute(status -> resolve(event));
        } catch (DataAccessException | TransactionException failure) {
            log.error("Merge round {} could not record entry {}", event.roundId(), event.entryId(), failure);
            throw new DomainException(FailureReason.UNKNOWN, "execution result unavailable: " + event.entryId());
        }
    }

    private MergeRound.ApplyResult resolve(MergeEntryResolvedEvent event) {
        MergeRound round = round(event.roundId());
        Entry entry = round.entries().stream().filter(candidate -> candidate.id().equals(event.entryId()))
                .findFirst().orElseThrow(() -> new DomainException(MergeResCode.ROUND_NOT_FOUND, event.entryId()));
        if (entry.result() != null) {
            return round.result();
        }
        if (event.outcome() == MergeEntryResolvedEvent.Outcome.APPLIED) {
            MergeCommitReceipt receipt = new MergeCommitReceipt(event.businessRef(),
                    event.sourceRevision(), event.targetRevision());
            round = round.finishApplied(entry.id(), operation(entry), receipt);
            MergeTargetEvent mergeTarget = new MergeTargetEvent(event.businessRef(), entry.evidence().family(),
                    entry.evidence().trackers().target(), round.target(), event.targetRevision(),
                    event.inherited(), event.created());
            if (event.inherited() || event.created()) {
                events.publishEvent(new MergeAppliedEvent(mergeTarget, System.currentTimeMillis()));
            }
        } else {
            round = finish(round, entry, result(entry, outcome(event.outcome()), operation(entry), null,
                    null, event.failure()));
        }
        return completeIfReady(round);
    }

    private MergeRound.ApplyResult completeIfReady(MergeRound round) {
        if (round.awaitingResults()) {
            mergeRepository.save(round);
            return null;
        }
        MergeRound finished = round.complete(System.currentTimeMillis());
        mergeRepository.save(finished);
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

    private MergeRound round(String id) {
        return mergeRepository.findById(id).orElseThrow(() -> new DomainException(MergeResCode.ROUND_NOT_FOUND, id));
    }

    /**
     * 一页决策只提交 evidence + decision；条目身份由服务端在登记时生成。
     */
    public record EntryRequest(Difference evidence, MergeDecision decision) {
    }
}
