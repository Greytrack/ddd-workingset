package alm.requirement.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import alm.requirement.application.port.RequirementMutationStore;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementResultCode;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.domain.event.CompleteDeleteEvent;
import sk.workingset.domain.LifecycleCommand;
import sk.workingset.domain.LifecycleResult;
import sk.workingset.domain.LocalId;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementLifecycleService {
    private final RequirementMutationStore requirements;
    private final LightTrackerItemQueryProvider items;
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public void delete2(LocalId id) {
        Requirement current = requirements.lockById(id).orElseThrow(() -> missing(id));
        LifecycleResult decision = current.delete2(LifecycleCommand.locked("permanent delete"));
        if (!decision.succeeded()) {
            throw DomainException.withMessage(RequirementResultCode.SOFT_DELETE_REQUIRED, id.value());
        }
        // The same source row lock serializes a concurrent derivation; deleted sources cannot acquire new dependents.
        if (items.hasSourceBackedDependents(id)) {
            throw DomainException.withMessage(FailureReason.PREPARING, "Derived items still depend on source " + id.value());
        }
        requirements.purge(current);
        eventPublisher.publishEvent(new CompleteDeleteEvent(id, current.trackerId(), Instant.now()));
    }

    private static DomainException missing(LocalId id) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, id.value());
    }
}
