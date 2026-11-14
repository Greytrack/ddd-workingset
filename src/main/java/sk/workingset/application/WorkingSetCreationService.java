package sk.workingset.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.application.event.WorkingSetCreationSubmitted;
import sk.workingset.application.event.TrackerItemsIndexed;
import sk.workingset.application.port.WorkingSetCreationStore;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetCreation.Failure;
import sk.workingset.domain.WorkingSetCreation.ItemOutcome;
import sk.workingset.domain.WorkingSetCreation.IndexProgress;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetCreation.Status;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.TrackerId;

public class WorkingSetCreationService {
    private final WorkingSetCreationStore store;
    private final WorkingSetRepository workingSets;
    private final WorkingSetApplicationService workingSetService;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final WorkingSetItemIndexer indexer;

    public WorkingSetCreationService(WorkingSetCreationStore store, WorkingSetRepository workingSets,
                                     WorkingSetApplicationService workingSetService,
                                     TransactionTemplate transaction, ApplicationEventPublisher events,
                                     WorkingSetItemIndexer indexer) {
        this.store = store;
        this.workingSets = workingSets;
        this.workingSetService = workingSetService;
        this.transaction = transaction;
        this.events = events;
        this.indexer = indexer;
    }

    @Transactional
    public WorkingSetCreation submit(WorkingSetCreation.Request request, Caller caller) {
        var target = WorkingSetId.of(request.newWorkingSetId());
        if (WorkingSet.DEFAULT_ID.equals(target)) {
            throw new IllegalArgumentException("The default working set cannot be a creation target");
        }
        var previous = store.findByTarget(target);
        if (previous.isPresent()) {
            return replay(previous.orElseThrow(), request);
        }
        if (workingSets.findWorkingSetForCreation(target).isPresent()) {
            throw new BindingFailure(Reason.WorkingSetAlreadyExists, target.value());
        }
        workingSets.requireReady(WorkingSetId.of(request.parentWorkingSetId()));
        var creation = WorkingSetCreation.submit(UUID.randomUUID().toString(), request, caller.id(), Instant.now());
        if (store.insert(creation)) {
            events.publishEvent(new WorkingSetCreationSubmitted(creation.id()));
            return creation;
        }
        return replay(store.findByTarget(target).orElseThrow(), request);
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
        var creation = find(id);
        return workingSets.findWorkingSetForCreation(WorkingSetId.of(creation.request().newWorkingSetId()))
                .orElseThrow(() -> new ApplicationFailure(FailureReason.NOT_FOUND,
                        "Working set is not created yet: " + creation.request().newWorkingSetId()));
    }

    public void execute(String id) {
        transaction.executeWithoutResult(tx -> {
            var creation = lock(id);
            if (creation.status() == Status.Pending) {
                store.update(creation.start(Instant.now()));
            }
        });
        boolean running;
        do {
            running = Boolean.TRUE.equals(transaction.execute(tx -> {
                var creation = lock(id);
                if (creation.status() != Status.Running) {
                    return false;
                }
                var updated = creation.stage() == Stage.INDEX_UNSHARED_TRACKERS
                        ? indexer.indexBatch(creation, tx) : executeItem(creation);
                store.update(updated);
                if (creation.stage() == Stage.INDEX_UNSHARED_TRACKERS && updated.status() != Status.Running) {
                    var scannedTrackers = updated.indexProgress().values().stream()
                            .map(IndexProgress::scannedChildTrackerId).filter(java.util.Objects::nonNull).sorted().toList();
                    events.publishEvent(new TrackerItemsIndexed(id, updated.request().newWorkingSetId(),
                            scannedTrackers, updated.completedAt()));
                }
                return updated.status() == Status.Running;
            }));
        } while (running);
    }

    private WorkingSetCreation executeItem(WorkingSetCreation creation) {
        String item = creation.currentItem();
        var target = WorkingSetId.of(creation.request().newWorkingSetId());
        var stage = creation.stage();
        if (stage == Stage.BIND_UNSHARED_TRACKERS && creation.failures().stream()
                .anyMatch(failure -> failure.stage() == Stage.COPY_UNSHARED_TRACKERS
                        && item.equals(failure.trackerId()))) {
            return creation.advance(ItemOutcome.Skipped, null, Instant.now());
        }
        try {
            boolean written = switch (stage) {
                case CREATE_WORKING_SET -> {
                    workingSetService.createChildForTask(WorkingSetId.of(creation.request().parentWorkingSetId()), target);
                    yield true;
                }
                case BIND_PRODUCT_SPACES -> workingSetService.bindSpaceForTask(target, item);
                case COPY_UNSHARED_TRACKERS -> workingSetService.copyUnsharedTrackerForTask(target, TrackerId.of(item));
                case BIND_UNSHARED_TRACKERS -> workingSetService.bindUnsharedTrackerForTask(target, TrackerId.of(item));
                case BIND_SHARED_TRACKERS -> workingSetService.bindSharedTrackerForTask(target, TrackerId.of(item));
                case INDEX_UNSHARED_TRACKERS -> throw new IllegalStateException("Indexing requires batch execution");
            };
            return creation.advance(written ? ItemOutcome.Succeeded : ItemOutcome.Skipped, null, Instant.now());
        } catch (BindingFailure failure) {
            return recordFailure(creation, item, failure.reason().name());
        } catch (ApplicationFailure failure) {
            if (failure.reason() == FailureReason.PREPARING) {
                throw failure;
            }
            return recordFailure(creation, item, failure.reason().name());
        }
    }

    private WorkingSetCreation recordFailure(WorkingSetCreation creation, String item, String reason) {
        var stage = creation.stage();
        String trackerId = switch (stage) {
            case COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, BIND_SHARED_TRACKERS -> item;
            default -> null;
        };
        String spaceId = stage == Stage.BIND_PRODUCT_SPACES ? item
                : trackerId == null ? null : workingSetService.trackerSpaceForTask(TrackerId.of(trackerId));
        var now = Instant.now();
        return creation.advance(ItemOutcome.Failed, new Failure(stage, spaceId, trackerId, null, reason, now), now);
    }

    private WorkingSetCreation lock(String id) {
        return store.find(id, true).orElseThrow(() -> notFound(id));
    }

    private static ApplicationFailure notFound(String id) {
        return new ApplicationFailure(FailureReason.NOT_FOUND, "Creation task not found: " + id);
    }
}
