package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.SavepointManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sk.RequestThreadHolder;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import sk.workingset.domain.event.TrackerItemsIndexedEvent;
import sk.workingset.domain.event.WorkingSetCreationSubmittedEvent;
import sk.workingset.domain.workingset.*;
import sk.workingset.domain.workingset.WorkingSetCreation.*;
import sk.workingset.error.BindingResCode;

import java.time.Instant;
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

    private final WorkingSetCreationRepository store;
    private final IdentifierGenerator ids;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final WorkingSetCommandService workingSetService;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final WorkingSetItemIndexer indexer;
    private final RelationEventStep relationEvents;
    private final TrackerQueryProvider trackerQueries;
    private final ClassificationCatalog classifications;

    private static DomainException notFound(String id) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, "Creation task not found: " + id);
    }

    private static boolean hasClassificationBinding(WorkingSet target, long spaceId, long classificationId) {
        return target.trackers().stream().anyMatch(binding -> binding.numericProductSpaceId() == spaceId
                && binding.numericClassificationId() == classificationId);
    }

    /**
     * 按 requestId 幂等受理创建请求；同键返回既有任务。
     */
    @Transactional
    public WorkingSetCreation submitNew(String requestId, Function<String, WorkingSetCreation.Request> request) {
        Optional<WorkingSetCreation> existing = store.findByRequest(requestId);
        if (existing.isPresent()) {
            WorkingSetCreation task = existing.get();
            return replay(task, withDefaultTrackers(request.apply(task.request().newWorkingSetId()), null));
        }
        return submit(request.apply(Long.toString(ids.nextId(null).longValue())));
    }

    /**
     * 受理创建请求；目标须非默认工作集且未被占用。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public WorkingSetCreation submit(WorkingSetCreation.Request request) {
        request = withDefaultTrackers(request, null);
        if (request.mode() != Mode.CREATE) {
            throw new IllegalArgumentException("Use submitExtension for an existing target");
        }
        WorkingSetId target = WorkingSetId.of(request.newWorkingSetId());
        if (WorkingSet.DEFAULT_ID.equals(target)) {
            throw new IllegalArgumentException("The default working set cannot be a creation target");
        }
        Optional<WorkingSetCreation> previous = store.findByTarget(target);
        if (previous.isPresent()) {
            return replay(previous.orElseThrow(), request);
        }
        if (workingSets.findByIdForCreation(target).isPresent()) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_ALREADY_EXISTS, target.value());
        }
        // 准备中的父工作集拒绝受理；不存在的父工作集由创建阶段记录为任务失败。
        workingSets.findById(WorkingSetId.of(request.parentWorkingSetId()));
        WorkingSetCreation creation = WorkingSetCreation.submit(Long.toString(ids.nextId(null).longValue()),
                request, RequestThreadHolder.getUserId(), Instant.now());
        if (store.save(creation)) {
            indexer.snapshot(creation);
            events.publishEvent(new WorkingSetCreationSubmittedEvent(creation.id()));
            return creation;
        }
        WorkingSetCreation winner = store.findByRequest(request.requestId())
                .or(() -> store.findByTarget(target)).orElseThrow();
        WorkingSetCreation.Request normalized = new WorkingSetCreation.Request(request.parentWorkingSetId(), winner.request().newWorkingSetId(),
                request.requestId(), request.productSpaceIds(), request.sharedTrackerIds(),
                request.unsharedParentTrackerIds(), request.name(), request.mode(), request.color());
        return replay(winner, normalized);
    }

    /**
     * 扩展已有子工作集：复用共享 Tracker 或派生非共享 Tracker，跳过已纳入的来源。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public WorkingSetCreation submitExtension(WorkingSetId target, String requestId,
                                              List<String> parentTrackerIds) {
        Optional<WorkingSetCreation> active = store.findActiveExtension(target);
        if (active.isPresent()) {
            if (active.get().request().requestId().equals(requestId)) {
                return active.get();
            }
            throw DomainException.withMessage(FailureReason.PREPARING, target.value());
        }
        WorkingSet targetSet = workingSets.findById(target)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, target.value()));
        WorkingSetId parentId = targetSet.requireParentId();
        TreeSet<String> requested = new TreeSet<>(parentTrackerIds == null ? List.of() : parentTrackerIds);
        TreeSet<String> spaces = new TreeSet<>();
        ArrayList<String> shared = new ArrayList<>();
        ArrayList<String> unshared = new ArrayList<>();
        for (String trackerId : requested) {
            TrackerId id = TrackerId.of(trackerId);
            Tracker source = trackers.findById(id).orElse(null);
            if (source != null && source.classification().folder()
                    && hasClassificationBinding(targetSet, source.numericProductSpaceId(), source.numericClassificationId())) {
                continue;
            }
            if (source != null && source.sharingMode() == SharingMode.SHARED) {
                if (targetSet.hasTrackerBinding(id)) continue;
                shared.add(trackerId);
            } else {
                boolean alreadyDerived = trackers.findDerivedTracker(target, id)
                        .filter(copy -> targetSet.hasTrackerBinding(copy.trackerId())
                                && trackerQueries.isVisible(copy.trackerId())).isPresent();
                if (alreadyDerived) continue;
                // Unknown sources reach the task so its failure is reported alongside valid selections.
                unshared.add(trackerId);
            }
            if (source != null) spaces.add(source.productSpaceId());
        }
        if (shared.isEmpty() && unshared.isEmpty() && spaces.isEmpty()) {
            throw DomainException.withMessage(BindingResCode.NO_DERIVABLE_TRACKER, target.value());
        }
        WorkingSetCreation.Request request = new WorkingSetCreation.Request(parentId.value(), target.value(), requestId,
                List.copyOf(spaces), shared, unshared, targetSet.name(), Mode.EXTEND);
        request = withDefaultTrackers(request, targetSet);
        WorkingSetCreation creation = WorkingSetCreation.submit(Long.toString(ids.nextId(null).longValue()),
                request, RequestThreadHolder.getUserId(), Instant.now());
        if (store.save(creation)) {
            indexer.snapshot(creation);
            events.publishEvent(new WorkingSetCreationSubmittedEvent(creation.id()));
            return creation;
        }
        Optional<WorkingSetCreation> concurrent = store.findActiveExtension(target);
        if (concurrent.isPresent()) {
            if (concurrent.get().request().requestId().equals(requestId)) {
                return concurrent.get();
            }
            throw DomainException.withMessage(FailureReason.PREPARING, target.value());
        }
        return replay(store.findByRequest(requestId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.PREPARING, target.value())), request);
    }

    /**
     * 任务受理前补齐默认 Tracker 选择。当前默认项为 Folder Tracker，未来可扩展其他默认 Tracker；
     * 它们与用户选中的非共享 Tracker 一起由 COPY_UNSHARED_TRACKERS 创建。
     */
    private WorkingSetCreation.Request withDefaultTrackers(WorkingSetCreation.Request request, WorkingSet existingTarget) {
        TreeSet<Long> unshared = new TreeSet<>(request.numericUnsharedTrackerIds());
        TreeSet<Long> shared = new TreeSet<>(request.numericSharedTrackerIds());
        WorkingSetId parent = WorkingSetId.of(request.numericParentWorkingSetId());
        List<Long> requiredClassifications = requiredTrackerClassifications();
        for (Long spaceId : request.numericProductSpaceIds()) {
            for (Long classificationId : requiredClassifications) {
                if (existingTarget != null && hasClassificationBinding(existingTarget, spaceId, classificationId)) {
                    continue;
                }
                trackerQueries.findDefaultTrackerId(parent, spaceId, classificationId)
                        .flatMap(trackers::findById).ifPresent(tracker -> {
                            var selection = tracker.sharingMode() == SharingMode.SHARED ? shared : unshared;
                            selection.add(tracker.trackerId().numericValue());
                        });
            }
        }
        return new WorkingSetCreation.Request(request.numericParentWorkingSetId(),
                request.numericTargetWorkingSetId(), request.requestId(), request.numericProductSpaceIds(),
                List.copyOf(shared), List.copyOf(unshared), request.name(), request.mode(), request.color());
    }

    private List<Long> requiredTrackerClassifications() {
        return classifications.findClassifications().stream().filter(Classification::folder)
                .map(Classification::numericId).toList();
    }

    private WorkingSetCreation replay(WorkingSetCreation existing, WorkingSetCreation.Request request) {
        if (!existing.request().equals(request)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_ALREADY_EXISTS, request.newWorkingSetId());
        }
        return existing;
    }

    /**
     * 按任务 id 读取创建任务。
     */
    public WorkingSetCreation find(String id) {
        return store.find(id).orElseThrow(() -> notFound(id));
    }

    /**
     * 任务上下文读取创建中的目标工作集；普通查询仍按 Preparing 过滤。
     */
    public WorkingSet findTarget(String id) {
        WorkingSetCreation creation = find(id);
        return workingSets.findByIdForCreation(WorkingSetId.of(creation.request().newWorkingSetId()))
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "Working set is not created yet: " + creation.request().newWorkingSetId()));
    }

    /**
     * 从已保存的进度继续执行，每次提交一个阶段项或一页索引。
     */
    public void execute(String id) {
        startIfPending(id);

        boolean hasMoreWork;
        do {
            hasMoreWork = advanceOneStepInTransaction(id);
        } while (hasMoreWork);
    }

    private void startIfPending(String id) {
        transaction.executeWithoutResult(tx -> {
            WorkingSetCreation creation = lock(id);
            if (creation.status() == Status.Pending) {
                store.save(creation.start(Instant.now()));
            }
        });
    }

    private boolean advanceOneStepInTransaction(String id) {
        return Boolean.TRUE.equals(transaction.execute(tx -> {
            // 每步重新锁定任务并读取进度，事件重投也从已提交的位置继续。
            WorkingSetCreation current = lock(id);
            if (current.status() != Status.Running) {
                return false;
            }

            WorkingSetCreation updated = executeStageStep(current, tx);
            store.save(updated);

            revealTrackersWhenExtensionFinishes(updated);
            publishEventWhenLeavingIndexStage(current, updated);
            return updated.status() == Status.Running;
        }));
    }

    private void revealTrackersWhenExtensionFinishes(WorkingSetCreation updated) {
        if (updated.status() == Status.Running || updated.request().mode() != Mode.EXTEND) {
            return;
        }
        Request request = updated.request();
        workingSetService.revealExtensionTrackers(WorkingSetId.of(request.numericTargetWorkingSetId()),
                request.numericUnsharedTrackerIds().stream().map(TrackerId::of).toList());
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
                scannedTrackers, Instant.now()));
    }

    /**
     * 分派本次事务处理的阶段。索引每次最多执行一页，其余阶段执行当前项。
     */
    private WorkingSetCreation executeStageStep(WorkingSetCreation creation, SavepointManager savepoints) {
        String item = creation.currentItem();
        Request request = creation.request();
        WorkingSetId target = WorkingSetId.of(request.numericTargetWorkingSetId());
        return switch (creation.stage()) {
            case CREATE_WORKING_SET -> executeScopeWrite(creation, item, () -> {
                workingSetService.createChildForTask(
                        WorkingSetId.of(request.numericParentWorkingSetId()), target,
                        request.name(), request.color(), Long.valueOf(creation.requestedBy()));
                return creation.advance(ItemOutcome.Succeeded, null, Instant.now());
            });
            case COPY_UNSHARED_TRACKERS -> executeScopeWrite(creation, item, () -> {
                boolean copied = workingSetService.deriveUnsharedTrackerForTask(target, TrackerId.of(item),
                        request.productSpaceIds(), request.mode() == Mode.CREATE,
                        Long.parseLong(creation.requestedBy()));
                return creation.advance(copied ? ItemOutcome.Succeeded : ItemOutcome.Skipped, null, Instant.now());
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

    private WorkingSetCreation publishRelations(WorkingSetCreation creation, WorkingSetId target, String item) {
        OriginType originType = creation.request().mode() == Mode.CREATE ? OriginType.CREATION : OriginType.EXTENSION;
        RelationEventStep.StepResult result = relationEvents.publish(target, TrackerId.of(item), originType, creation.id());
        if (result.failed() > 0) {
            return recordFailure(creation, item, "RELATION_EVENT_FAILED");
        }
        return creation.advance(result.skipped() ? ItemOutcome.Skipped : ItemOutcome.Succeeded, null, Instant.now());
    }

    private WorkingSetCreation bindProductAndTrackers(WorkingSetCreation creation, WorkingSetId target) {
        var failedCopies = creation.failures().stream()
                .filter(failure -> failure.stage() == Stage.COPY_UNSHARED_TRACKERS)
                .map(Failure::trackerId)
                .collect(Collectors.toSet());
        var binding = workingSetService.bindProductAndTrackersForTask(target, creation.request(), failedCopies,
                requiredTrackerClassifications());
        return creation.recordBindingResult(binding.changed(), binding.failures(), Instant.now());
    }

    private WorkingSetCreation recordFailure(WorkingSetCreation creation, String item, String reason) {
        WorkingSetCreation.Stage stage = creation.stage();
        String trackerId = switch (stage) {
            case COPY_UNSHARED_TRACKERS, PUBLISH_RELATION_EVENTS -> item;
            default -> null;
        };
        String spaceId = trackerId == null ? null : workingSetService.trackerSpaceForTask(TrackerId.of(trackerId));
        Instant now = Instant.now();
        return creation.advance(ItemOutcome.Failed, new Failure(stage, spaceId, trackerId, null, reason, now), now);
    }

    private WorkingSetCreation lock(String id) {
        return store.findForUpdate(id).orElseThrow(() -> notFound(id));
    }
}
