package alm.requirement.application.command;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import alm.requirement.application.port.RequirementMutationStore;
import alm.requirement.domain.Requirement;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.event.CompleteDeleteEvent;
import sk.workingset.domain.LifecycleCommand;
import sk.workingset.domain.LifecycleResult;
import sk.workingset.domain.LocalId;

public class RequirementLifecycleService {
    private final RequirementMutationStore requirements;
    private final ApplicationEventPublisher eventPublisher;

    public RequirementLifecycleService(RequirementMutationStore requirements,
                                       ApplicationEventPublisher eventPublisher) {
        this.requirements = requirements;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void delete2(LocalId id) {
        Requirement current = requirements.lockById(id).orElseThrow(() -> missing(id));
        LifecycleResult decision = current.delete2(LifecycleCommand.locked("permanent delete"));
        if (!decision.succeeded()) {
            throw DomainException.withMessage(FailureReason.DELETED, id.value());
        }
        requirements.purge(current);
        eventPublisher.publishEvent(new CompleteDeleteEvent(id, current.trackerId(), Instant.now()));
    }

    private static DomainException missing(LocalId id) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, id.value());
    }
}
