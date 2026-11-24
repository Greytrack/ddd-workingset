package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemsIndexedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.WorkingSetCreationSubmittedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.*;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreationRepository;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.SavepointManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 工作集创建/扩展任务的受理与执行：按阶段逐步提交，部分成功保留失败明细（T7）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingSetCreationService {
    private static final List<Long> REQUIRED_TRACKER_CLASSIFICATIONS = List.of(10070101L);

    private final WorkingSetCreationRepository workingSetCreationRepository;
        private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final WorkingSetCommandService workingSetService;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final WorkingSetItemIndexer indexer;
    private final RelationEventStep relationEvents;
    private final TrackerQueryProvider trackerQueries;

    private static DomainException notFound(String id) {
        return new DomainException(FailureReason.NOT_FOUND, "Creation task not found: " + id);
    }

    private static boolean hasClassificationBinding(WorkingSet target, long spaceId, long classificationId) {
        return target.trackers().stream().anyMatch(binding -> binding.numericProductSpaceId() == spaceId
                && binding.numericClassificationId() == classificationId);
    }

    private static void validateBaselines(Collection<Long> trackerIds, Map<Long, Long> overrides, long acceptedAt) {
        if (!trackerIds.containsAll(overrides.keySet()) || overrides.values().stream().anyMatch(time -> time > acceptedAt)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST,
                    "Baseline time must reference a selected Tracker and cannot be in the future");
        }
    }

    private static List<Long> explicit(List<Long> ids, Request request) {
        return ids.stream().filter(id -> !request.automaticTrackerIds().contains(id)).toList();
    }

    /**
     * 按 requestId 幂等受理创建请求；同键返回既有任务。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkingSetCreation submitNew(String requestId, Function<Long, WorkingSetCreation.Request> request) {
        Optional<WorkingSetCreation> existing = workingSetCreationRepository.findByRequest(requestId);
        if (existing.isPresent()) {
            WorkingSetCreation task = existing.get();
            return replayCreate(task, request.apply(task.request().numericTargetWorkingSetId()));
        }
        return submit(request.apply(Ids.nextId()));
    }

    /**
     * 受理创建请求；目标须非默认工作集且未被占用。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkingSetCreation submit(WorkingSetCreation.Request request) {
        if (request.mode() != Mode.CREATE) {
            throw new IllegalArgumentException("Use submitExtension for an existing target");
        }
        Long target = Long.valueOf(request.newWorkingSetId());
        if (WorkingSet.DEFAULT_ID.equals(target)) {
            throw new IllegalArgumentException("The default working set cannot be a creation target");
        }
        Optional<WorkingSetCreation> previous = workingSetCreationRepository.findByTarget(target);
        if (previous.isPresent()) {
            return replayCreate(previous.orElseThrow(), request);
        }
        if (workingSets.findByIdForCreation(target).isPresent()) {
            throw new DomainException(BindingResCode.WORKING_SET_ALREADY_EXISTS, Long.toString(target));
        }
        // 准备中的父工作集拒绝受理；不存在的父工作集由创建阶段记录为任务失败。
        workingSets.findById(Long.valueOf(request.parentWorkingSetId()));
        long acceptedAt = workingSetCreationRepository.acceptanceTime();
        validateBaselines(request.selectedTrackerIds(), request.baselineOverrides(), acceptedAt);
        WorkingSetCreation creation = WorkingSetCreation.submit(Long.toString(Ids.nextId()),
                withDefaultTrackers(request, null), Long.toString(AuthorizationScope.currentUserId()), acceptedAt);
        if (workingSetCreationRepository.save(creation)) {
            events.publishEvent(new WorkingSetCreationSubmittedEvent(creation.id()));
            return creation;
        }
        WorkingSetCreation winner = workingSetCreationRepository.findByRequest(request.requestId())
                .or(() -> workingSetCreationRepository.findByTarget(target)).orElseThrow();
        return replayCreate(winner, request);
    }

    /**
     * 扩展已有子工作集：复用共享 Tracker 或派生非共享 Tracker，跳过已纳入的来源。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkingSetCreation submitExtension(Long target, String requestId,
                                              List<Long> parentTrackerIds, Map<Long, Long> overrides) {
        Map<Long, Long> baselineOverrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        List<Long> submittedIds = parentTrackerIds == null ? List.of()
                : parentTrackerIds.stream().distinct().sorted().toList();
        Optional<WorkingSetCreation> existing = workingSetCreationRepository.findByRequest(requestId);
        if (existing.isPresent()) {
            return replayExtension(existing.orElseThrow(), target, submittedIds, baselineOverrides);
        }
        Optional<WorkingSetCreation> active = workingSetCreationRepository.findActiveExtension(target);
        if (active.isPresent()) {
            if (active.get().request().requestId().equals(requestId)) {
                return replayExtension(active.get(), target, submittedIds, baselineOverrides);
            }
            throw new DomainException(FailureReason.PREPARING, Long.toString(target));
        }
        WorkingSet targetSet = workingSets.findById(target)
                .orElseThrow(() -> new DomainException(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(target)));
        Long parentId = targetSet.requireParentId();
        long acceptedAt = workingSetCreationRepository.acceptanceTime();
        validateBaselines(submittedIds, baselineOverrides, acceptedAt);
        TreeSet<String> spaces = new TreeSet<>();
        ArrayList<Long> shared = new ArrayList<>();
        ArrayList<Long> unshared = new ArrayList<>();
        for (Long trackerId : submittedIds) {
            Long id = trackerId;
            Tracker source = trackers.findById(id).orElse(null);
            if (source != null && REQUIRED_TRACKER_CLASSIFICATIONS.contains(source.numericClassificationId())
                    && hasClassificationBinding(targetSet, source.productSpaceId(), source.numericClassificationId())) {
                continue;
            }
            if (source != null && source.sharingMode() == SharingMode.SHARED) {
                if (targetSet.hasTrackerBinding(id)) continue;
                shared.add(trackerId);
            } else {
                boolean alreadyDerived = trackers.findDerivedTracker(target, id)
                        .filter(copy -> targetSet.hasTrackerBinding(copy.trackerId())).isPresent();
                if (alreadyDerived) continue;
                // Unknown sources reach the task so its failure is reported alongside valid selections.
                unshared.add(trackerId);
            }
            if (source != null) spaces.add(source.productSpaceId().toString());
        }
        if (shared.isEmpty() && unshared.isEmpty() && spaces.isEmpty()) {
            throw new DomainException(BindingResCode.NO_DERIVABLE_TRACKER, Long.toString(target));
        }
        WorkingSetCreation.Request request = new WorkingSetCreation.Request(parentId, target, requestId,
                spaces.stream().map(Long::valueOf).toList(), shared, unshared, targetSet.name(), Mode.EXTEND,
                null, baselineOverrides, List.of(), submittedIds);
        request = withDefaultTrackers(request, targetSet);
        WorkingSetCreation creation = WorkingSetCreation.submit(Long.toString(Ids.nextId()),
                request, Long.toString(AuthorizationScope.currentUserId()), acceptedAt);
        if (workingSetCreationRepository.save(creation)) {
            events.publishEvent(new WorkingSetCreationSubmittedEvent(creation.id()));
            return creation;
        }
        // 冲突插入已等待获胜事务提交，重新读取该请求键以区分重放与目标正在扩展。
        return replayExtension(workingSetCreationRepository.findByRequest(requestId)
                        .orElseThrow(() -> new DomainException(FailureReason.PREPARING, Long.toString(target))),
                target, submittedIds, baselineOverrides);
    }

    /**
     * 任务受理前补齐默认 Tracker 选择。当前默认项为 Folder Tracker，未来可扩展其他默认 Tracker；
     * 它们与用户选中的非共享 Tracker 一起由 COPY_UNSHARED_TRACKERS 创建。
     */
    private WorkingSetCreation.Request withDefaultTrackers(WorkingSetCreation.Request request, WorkingSet existingTarget) {
        TreeSet<Long> unshared = new TreeSet<>(request.numericUnsharedTrackerIds());
        TreeSet<Long> shared = new TreeSet<>(request.numericSharedTrackerIds());
        List<Long> automatic = new ArrayList<>();
        Long parent = request.numericParentWorkingSetId();
        for (Long spaceId : request.numericProductSpaceIds()) {
            for (Long classificationId : REQUIRED_TRACKER_CLASSIFICATIONS) {
                if (existingTarget != null && hasClassificationBinding(existingTarget, spaceId, classificationId)) {
                    continue;
                }
                trackerQueries.findDefaultTrackerId(parent, spaceId, classificationId)
                        .flatMap(trackers::findById).ifPresent(tracker -> {
                            TreeSet<Long> selection = tracker.sharingMode() == SharingMode.SHARED ? shared : unshared;
                            if (selection.add(tracker.trackerId())) {
                                automatic.add(tracker.trackerId());
                            }
                        });
            }
        }
        return new WorkingSetCreation.Request(request.numericParentWorkingSetId(),
                request.numericTargetWorkingSetId(), request.requestId(), request.numericProductSpaceIds(),
                List.copyOf(shared), List.copyOf(unshared), request.name(), request.mode(), request.color(),
                request.baselineOverrides(), automatic, request.submittedExtensionTrackerIds());
    }

    private WorkingSetCreation replayCreate(WorkingSetCreation existing, Request requested) {
        Request saved = existing.request();
        if (saved.mode() != Mode.CREATE || saved.mode() != requested.mode() || !saved.requestId().equals(requested.requestId())
                || !saved.numericParentWorkingSetId().equals(requested.numericParentWorkingSetId())
                || !saved.numericProductSpaceIds().equals(requested.numericProductSpaceIds())
                || !explicit(saved.numericSharedTrackerIds(), saved).equals(requested.numericSharedTrackerIds())
                || !explicit(saved.numericUnsharedTrackerIds(), saved).equals(requested.numericUnsharedTrackerIds())
                || !Objects.equals(saved.name(), requested.name()) || !Objects.equals(saved.color(), requested.color())
                || !saved.baselineOverrides().equals(requested.baselineOverrides())) {
            throw new DomainException(BindingResCode.WORKING_SET_ALREADY_EXISTS, requested.newWorkingSetId());
        }
        return existing;
    }

    private WorkingSetCreation replayExtension(WorkingSetCreation existing, Long target,
                                               List<Long> submittedIds, Map<Long, Long> overrides) {
        Request saved = existing.request();
        if (saved.mode() != Mode.EXTEND || !saved.numericTargetWorkingSetId().equals(target)
                || !saved.submittedExtensionTrackerIds().equals(submittedIds) || !saved.baselineOverrides().equals(overrides)) {
            throw new DomainException(BindingResCode.WORKING_SET_ALREADY_EXISTS, Long.toString(target));
        }
        return existing;
    }

    /**
     * 按任务 id 读取创建任务。
     */
    public WorkingSetCreation find(String id) {
        return workingSetCreationRepository.find(id).orElseThrow(() -> notFound(id));
    }

    /**
     * 任务上下文读取创建中的目标工作集；普通查询仍按 Preparing 过滤。
     */
    public WorkingSet findTarget(String id) {
        WorkingSetCreation creation = find(id);
        return workingSets.findByIdForCreation(Long.valueOf(creation.request().newWorkingSetId()))
                .orElseThrow(() -> new DomainException(FailureReason.NOT_FOUND,
                        "Working set is not created yet: " + creation.request().newWorkingSetId()));
    }

    /**
     * 从已保存的进度继续执行，每次提交一个阶段项或一页索引。
     */
    public void execute(String id) {
        startIfPending(id);
        TransactionTemplate stepTransaction = new TransactionTemplate(transaction.getTransactionManager(), transaction);
        stepTransaction.setIsolationLevel(Isolation.SERIALIZABLE.value());

        boolean hasMoreWork;
        do {
            hasMoreWork = advanceWithConcurrencyRetry(id, stepTransaction);
        } while (hasMoreWork);
    }

    private void startIfPending(String id) {
        transaction.executeWithoutResult(tx -> {
            WorkingSetCreation creation = lock(id);
            if (creation.status() == Status.Pending) {
                workingSetCreationRepository.save(creation.start(System.currentTimeMillis()));
            }
        });
    }

    private boolean advanceWithConcurrencyRetry(String id, TransactionTemplate stepTransaction) {
        for (int attempt = 1; ; attempt++) {
            try {
                return advanceOneStepInTransaction(id, stepTransaction);
            } catch (PessimisticLockingFailureException conflict) {
                if (attempt == 3) {
                    throw conflict;
                }
            }
        }
    }

    private boolean advanceOneStepInTransaction(String id, TransactionTemplate stepTransaction) {
        return Boolean.TRUE.equals(stepTransaction.execute(tx -> {
            // 每步重新锁定任务并读取进度，事件重投也从已提交的位置继续。
            WorkingSetCreation current = lock(id);
            if (current.status() != Status.Running) {
                return false;
            }

            WorkingSetCreation updated = executeStageStep(current, tx);
            workingSetCreationRepository.save(updated);

            publishEventWhenLeavingIndexStage(current, updated);
            return updated.status() == Status.Running;
        }));
    }

    private void publishEventWhenLeavingIndexStage(WorkingSetCreation current, WorkingSetCreation updated) {
        if (current.stage() != Stage.INDEX_UNSHARED_TRACKERS || updated.stage() == Stage.INDEX_UNSHARED_TRACKERS) {
            return;
        }
        List<String> scannedTrackers = updated.indexProgress().values().stream()
                .map(IndexProgress::scannedChildTrackerId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        events.publishEvent(new TrackerItemsIndexedEvent(updated.id(), updated.request().newWorkingSetId(),
                scannedTrackers, System.currentTimeMillis()));
    }

    /**
     * 分派本次事务处理的阶段。索引每次最多执行一页，其余阶段执行当前项。
     */
    private WorkingSetCreation executeStageStep(WorkingSetCreation creation, SavepointManager savepoints) {
        String item = creation.currentItem();
        Request request = creation.request();
        Long target = request.numericTargetWorkingSetId();
        return switch (creation.stage()) {
            case CREATE_WORKING_SET -> executeScopeWrite(creation, item, () -> {
                workingSetService.createChildForTask(
                        request.numericParentWorkingSetId(), target,
                        request.name(), request.color(), Long.valueOf(creation.requestedBy()));
                return creation.advance(ItemOutcome.Succeeded, null, System.currentTimeMillis());
            });
            case COPY_UNSHARED_TRACKERS -> executeScopeWrite(creation, item, () -> {
                boolean copied = workingSetService.deriveUnsharedTrackerForTask(target, Long.valueOf(item),
                        request.productSpaceIds(),
                        Long.parseLong(creation.requestedBy()));
                return creation.advance(copied ? ItemOutcome.Succeeded : ItemOutcome.Skipped, null, System.currentTimeMillis());
            });
            case BIND_PRODUCT_AND_TRACKER ->
                    executeScopeWrite(creation, item, () -> bindProductAndTrackers(creation, target));
            case INDEX_UNSHARED_TRACKERS -> indexer.indexBatch(creation, savepoints);
            case PUBLISH_RELATION_EVENTS -> publishRelations(creation, target, item);
        };
    }

    /**
     * 将本体创建、派生和绑定的领域拒绝记录到任务；索引与关系事件各自处理失败。
     */
    private WorkingSetCreation executeScopeWrite(WorkingSetCreation creation, String item,
                                                 Supplier<WorkingSetCreation> action) {
        try {
            return action.get();
        } catch (DomainException failure) {
            if (failure.getResCode() instanceof BindingResCode binding) {
                return recordFailure(creation, item, binding.reason());
            }
            FailureReason reason = FailureReason.of(failure);
            if (reason == FailureReason.PREPARING) {
                throw failure;
            }
            return recordFailure(creation, item, reason.name());
        }
    }

    private WorkingSetCreation publishRelations(WorkingSetCreation creation, Long target, String item) {
        OriginType originType = creation.request().mode() == Mode.CREATE ? OriginType.CREATION : OriginType.EXTENSION;
        RelationEventStep.StepResult result = relationEvents.publish(target, Long.valueOf(item), originType, creation.id());
        if (result.failed() > 0) {
            return recordFailure(creation, item, "RELATION_EVENT_FAILED");
        }
        return creation.advance(result.skipped() ? ItemOutcome.Skipped : ItemOutcome.Succeeded, null, System.currentTimeMillis());
    }

    private WorkingSetCreation bindProductAndTrackers(WorkingSetCreation creation, Long target) {
        Set<String> failedCopies = creation.failures().stream()
                .filter(failure -> failure.stage() == Stage.COPY_UNSHARED_TRACKERS)
                .map(Failure::trackerId)
                .collect(Collectors.toSet());
        WorkingSetCommandService.BindingResult binding = workingSetService.bindProductAndTrackersForTask(target, creation.request(), failedCopies,
                REQUIRED_TRACKER_CLASSIFICATIONS);
        return creation.recordBindingResult(binding.changed(), binding.failures(), System.currentTimeMillis());
    }

    private WorkingSetCreation recordFailure(WorkingSetCreation creation, String item, String reason) {
        WorkingSetCreation.Stage stage = creation.stage();
        String trackerId = switch (stage) {
            case COPY_UNSHARED_TRACKERS, PUBLISH_RELATION_EVENTS -> item;
            default -> null;
        };
        String spaceId = trackerId == null ? null : workingSetService.trackerSpaceForTask(Long.valueOf(trackerId));
        long now = System.currentTimeMillis();
        return creation.advance(ItemOutcome.Failed, new Failure(stage, spaceId, trackerId, null, reason, now), now);
    }

    private WorkingSetCreation lock(String id) {
        return workingSetCreationRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
    }
}
