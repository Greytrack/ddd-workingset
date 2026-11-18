package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.domain.event.WorkingSetCreationSubmittedEvent;
import sk.workingset.domain.event.TrackerItemsIndexedEvent;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetCreationRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetCreation.Failure;
import sk.workingset.domain.WorkingSetCreation.Mode;
import sk.workingset.domain.WorkingSetCreation.ItemOutcome;
import sk.workingset.domain.WorkingSetCreation.IndexProgress;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetCreation.Status;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import sk.workingset.domain.TrackerId;

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


    @Transactional
    public WorkingSetCreation submitNew(String requestId, Function<String, WorkingSetCreation.Request> request,
                                        String caller) {
        java.util.Optional<WorkingSetCreation> existing = store.findByRequest(requestId);
        if (existing.isPresent()) {
            WorkingSetCreation task = existing.get();
            return replay(task, request.apply(task.request().newWorkingSetId()));
        }
        return submit(request.apply(Long.toString(ids.nextId(null).longValue())), caller);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public WorkingSetCreation submit(WorkingSetCreation.Request request, String caller) {
        if (request.mode() != Mode.CREATE) {
            throw new IllegalArgumentException("Use submitExtension for an existing target");
        }
        WorkingSetId target = WorkingSetId.of(request.newWorkingSetId());
        if (WorkingSet.DEFAULT_ID.equals(target)) {
            throw new IllegalArgumentException("The default working set cannot be a creation target");
        }
        java.util.Optional<WorkingSetCreation> previous = store.findByTarget(target);
        if (previous.isPresent()) {
            return replay(previous.orElseThrow(), request);
        }
        if (workingSets.findWorkingSetForCreation(target).isPresent()) {
            throw new BindingFailure(Reason.WorkingSetAlreadyExists, target.value());
        }
        workingSets.requireReady(WorkingSetId.of(request.parentWorkingSetId()));
        WorkingSetCreation creation = WorkingSetCreation.submit(Long.toString(ids.nextId(null).longValue()), request, caller, Instant.now());
        if (store.insert(creation)) {
            indexer.snapshot(creation);
            events.publishEvent(new WorkingSetCreationSubmittedEvent(creation.id()));
            return creation;
        }
        WorkingSetCreation winner = store.findByRequest(request.requestId())
                .or(() -> store.findByTarget(target)).orElseThrow();
        WorkingSetCreation.Request normalized = new WorkingSetCreation.Request(request.parentWorkingSetId(), winner.request().newWorkingSetId(),
                request.requestId(), request.productSpaceIds(), request.sharedTrackerIds(),
                request.unsharedParentTrackerIds(), request.name(), request.mode());
        return replay(winner, normalized);
    }

    /** 扩展已有子工作集：复用共享 Tracker 或派生非共享 Tracker，跳过已纳入的来源。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public WorkingSetCreation submitExtension(WorkingSetId target, String requestId,
                                              List<String> parentTrackerIds, String caller) {
        java.util.Optional<WorkingSetCreation> active = store.findActiveExtension(target);
        if (active.isPresent()) {
            if (active.get().request().requestId().equals(requestId)) {
                return active.get();
            }
            throw DomainException.withMessage(FailureReason.PREPARING, target.value());
        }
        workingSets.requireReady(target);
        WorkingSet targetSet = workingSets.findWorkingSet(target)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, target.value()));
        WorkingSetId parentId = targetSet.requireParentId();
        TreeSet<String> requested = new TreeSet<>(parentTrackerIds == null ? List.of() : parentTrackerIds);
        TreeSet<String> spaces = new TreeSet<String>();
        ArrayList<String> shared = new ArrayList<String>();
        ArrayList<String> unshared = new ArrayList<String>();
        for (String trackerId : requested) {
            TrackerId id = TrackerId.of(trackerId);
            sk.workingset.domain.Tracker source = trackers.findTracker(id).orElse(null);
            if (source != null && source.sharingMode() == SharingMode.SHARED) {
                if (targetSet.hasTrackerBinding(id)) continue;
                shared.add(trackerId);
            } else {
                boolean alreadyDerived = trackers.findDerivedTracker(target, id)
                        .filter(copy -> targetSet.hasTrackerBinding(copy.trackerId())
                                && trackers.isVisible(copy.trackerId())).isPresent();
                if (alreadyDerived) continue;
                // Unknown sources reach the task so its failure is reported alongside valid selections.
                unshared.add(trackerId);
            }
            if (source != null) spaces.add(source.productSpaceId());
        }
        if (shared.isEmpty() && unshared.isEmpty()) {
            throw new BindingFailure(Reason.NoDerivableTracker, target.value());
        }
        WorkingSetCreation.Request request = new WorkingSetCreation.Request(parentId.value(), target.value(), requestId,
                List.copyOf(spaces), shared, unshared, targetSet.name(), Mode.EXTEND);
        WorkingSetCreation creation = WorkingSetCreation.submit(Long.toString(ids.nextId(null).longValue()), request, caller, Instant.now());
        if (store.insert(creation)) {
            indexer.snapshot(creation);
            events.publishEvent(new WorkingSetCreationSubmittedEvent(creation.id()));
            return creation;
        }
        java.util.Optional<WorkingSetCreation> concurrent = store.findActiveExtension(target);
        if (concurrent.isPresent()) {
            if (concurrent.get().request().requestId().equals(requestId)) {
                return concurrent.get();
            }
            throw DomainException.withMessage(FailureReason.PREPARING, target.value());
        }
        return replay(store.findByRequest(requestId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.PREPARING, target.value())), request);
    }

    private WorkingSetCreation replay(WorkingSetCreation existing, WorkingSetCreation.Request request) {
        if (!existing.request().equals(request)) {
            throw new BindingFailure(Reason.WorkingSetAlreadyExists, request.newWorkingSetId());
        }
        return existing;
    }

    public WorkingSetCreation find(String id) {
        return store.find(id, false).orElseThrow(() -> notFound(id));
    }

    /** 任务上下文读取创建中的目标工作集；普通查询仍按 Preparing 过滤。 */
    public WorkingSet findTarget(String id) {
        WorkingSetCreation creation = find(id);
        return workingSets.findWorkingSetForCreation(WorkingSetId.of(creation.request().newWorkingSetId()))
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "Working set is not created yet: " + creation.request().newWorkingSetId()));
    }

    public void execute(String id) {
        transaction.executeWithoutResult(tx -> {
            WorkingSetCreation creation = lock(id);
            if (creation.status() == Status.Pending) {
                store.update(creation.start(Instant.now()));
            }
        });
        boolean running;
        do {
            running = Boolean.TRUE.equals(transaction.execute(tx -> {
                WorkingSetCreation creation = lock(id);
                if (creation.status() != Status.Running) {
                    return false;
                }
                WorkingSetCreation updated = creation.stage() == Stage.INDEX_UNSHARED_TRACKERS
                        ? indexer.indexBatch(creation, tx) : executeItem(creation);
                store.update(updated);
                if (updated.status() != Status.Running && updated.request().mode() == Mode.EXTEND) {
                    workingSetService.revealExtensionTrackers(WorkingSetId.of(updated.request().newWorkingSetId()),
                            updated.request().unsharedParentTrackerIds().stream().map(TrackerId::of).toList());
                }
                if (creation.stage() == Stage.INDEX_UNSHARED_TRACKERS
                        && updated.stage() != Stage.INDEX_UNSHARED_TRACKERS) {
                    List<String> scannedTrackers = updated.indexProgress().values().stream()
                            .map(IndexProgress::scannedChildTrackerId).filter(java.util.Objects::nonNull).sorted().toList();
                    events.publishEvent(new TrackerItemsIndexedEvent(id, updated.request().newWorkingSetId(),
                            scannedTrackers, Instant.now()));
                }
                return updated.status() == Status.Running;
            }));
        } while (running);
    }

    private WorkingSetCreation executeItem(WorkingSetCreation creation) {
        String item = creation.currentItem();
        WorkingSetId target = WorkingSetId.of(creation.request().newWorkingSetId());
        WorkingSetCreation.Stage stage = creation.stage();
        if (stage == Stage.PUBLISH_RELATION_EVENTS) {
            OriginType originType = creation.request().mode() == Mode.CREATE ? OriginType.CREATION : OriginType.EXTENSION;
            RelationEventStep.StepResult result = relationEvents.publish(target, TrackerId.of(item), originType, creation.id());
            if (result.failed() > 0) {
                return recordFailure(creation, item, "RELATION_EVENT_FAILED");
            }
            return creation.advance(result.skipped() ? ItemOutcome.Skipped : ItemOutcome.Succeeded, null,
                    Instant.now());
        }
        if (stage == Stage.BIND_UNSHARED_TRACKERS && creation.failures().stream()
                .anyMatch(failure -> failure.stage() == Stage.COPY_UNSHARED_TRACKERS
                        && item.equals(failure.trackerId()))) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        try {
            boolean written = switch (stage) {
                case CREATE_WORKING_SET -> {
                    workingSetService.createChildForTask(
                            WorkingSetId.of(creation.request().parentWorkingSetId()), target,
                            creation.request().name());
                    yield true;
                }
                case BIND_PRODUCT_SPACES -> workingSetService.bindSpaceForTask(target, item);
                case COPY_UNSHARED_TRACKERS -> workingSetService.copyUnsharedTrackerForTask(target, TrackerId.of(item),
                        creation.request().mode() == Mode.CREATE);
                case BIND_UNSHARED_TRACKERS -> workingSetService.bindUnsharedTrackerForTask(target, TrackerId.of(item));
                case BIND_SHARED_TRACKERS -> workingSetService.bindSharedTrackerForTask(target, TrackerId.of(item));
                case INDEX_UNSHARED_TRACKERS -> throw new IllegalStateException("Indexing requires batch execution");
                case PUBLISH_RELATION_EVENTS -> throw new IllegalStateException("Relation events require batch execution");
            };
            return creation.advance(written ? ItemOutcome.Succeeded : ItemOutcome.Skipped, null, Instant.now());
        } catch (BindingFailure failure) {
            return recordFailure(creation, item, failure.reason().name());
        } catch (DomainException failure) {
            FailureReason reason = FailureReason.of(failure);
            if (reason == FailureReason.PREPARING) {
                throw failure;
            }
            return recordFailure(creation, item, reason.name());
        }
    }

    private WorkingSetCreation recordFailure(WorkingSetCreation creation, String item, String reason) {
        WorkingSetCreation.Stage stage = creation.stage();
        String trackerId = switch (stage) {
            case COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, BIND_SHARED_TRACKERS,
                    PUBLISH_RELATION_EVENTS -> item;
            default -> null;
        };
        String spaceId = stage == Stage.BIND_PRODUCT_SPACES ? item
                : trackerId == null ? null : workingSetService.trackerSpaceForTask(TrackerId.of(trackerId));
        Instant now = Instant.now();
        return creation.advance(ItemOutcome.Failed, new Failure(stage, spaceId, trackerId, null, reason, now), now);
    }

    private WorkingSetCreation lock(String id) {
        return store.find(id, true).orElseThrow(() -> notFound(id));
    }

    private static DomainException notFound(String id) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, "Creation task not found: " + id);
    }
}
