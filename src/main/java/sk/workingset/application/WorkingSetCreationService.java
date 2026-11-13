package sk.workingset.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.application.event.WorkingSetCreationSubmitted;
import sk.workingset.application.port.WorkingSetCreationStore;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetCreation.Status;
import sk.workingset.domain.WorkingSetId;

public class WorkingSetCreationService {
    private final WorkingSetCreationStore store;
    private final WorkingSetRepository workingSets;
    private final WorkingSetApplicationService workingSetService;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    public WorkingSetCreationService(WorkingSetCreationStore store, WorkingSetRepository workingSets,
                                     WorkingSetApplicationService workingSetService,
                                     TransactionTemplate transaction, ApplicationEventPublisher events) {
        this.store = store;
        this.workingSets = workingSets;
        this.workingSetService = workingSetService;
        this.transaction = transaction;
        this.events = events;
    }

    @Transactional
    public WorkingSetCreation submit(WorkingSetCreation.Request request, Caller caller) {
        if (request.hasSelection()) {
            throw new ApplicationFailure(FailureReason.CAPABILITY_NOT_SUPPORTED, "Range initialization is not available");
        }
        var target = WorkingSetId.of(request.newWorkingSetId());
        if (WorkingSet.DEFAULT_ID.equals(target)) {
            throw new IllegalArgumentException("The default working set cannot be a creation target");
        }
        var previous = store.findByTarget(target);
        if (previous.isPresent()) {
            return replay(previous.orElseThrow(), request);
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

    public void execute(String id) {
        transaction.executeWithoutResult(tx -> {
            var creation = lock(id);
            if (creation.status() == Status.Pending) {
                store.update(creation.start(Instant.now()));
            }
        });
        transaction.executeWithoutResult(tx -> {
            var creation = lock(id);
            if (creation.status() == Status.Running) {
                store.update(createBody(creation));
            }
        });
    }

    private WorkingSetCreation createBody(WorkingSetCreation creation) {
        var request = creation.request();
        try {
            workingSetService.createChildForTask(WorkingSetId.of(request.parentWorkingSetId()),
                    WorkingSetId.of(request.newWorkingSetId()));
            return creation.completeBody(Instant.now());
        } catch (BindingFailure failure) {
            return creation.failBody(failure.reason().name(), Instant.now());
        } catch (ApplicationFailure failure) {
            return creation.failBody(failure.reason().name(), Instant.now());
        }
    }

    private WorkingSetCreation lock(String id) {
        return store.find(id, true).orElseThrow(() -> notFound(id));
    }

    private static ApplicationFailure notFound(String id) {
        return new ApplicationFailure(FailureReason.NOT_FOUND, "Creation task not found: " + id);
    }
}
