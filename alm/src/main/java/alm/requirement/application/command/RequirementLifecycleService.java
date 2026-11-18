package alm.requirement.application.command;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import alm.requirement.application.port.RequirementMutationStore;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.event.CompleteDeleteEvent;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.LifecycleCommand;
import sk.workingset.domain.LocalId;

/**
 * 协调永久删除与仍依赖该内容的工作集变体。
 *
 * <p>D4 方案 A：Tracker 锁定、COW 依赖判定与 LTI 移除均由 SK 承担（{@link TrackerItemLifecyclePort}）；
 * 本服务只删除业务对象，并在提交后通过 {@link CompleteDeleteEvent} 事件通知 SK 清理 LTI。
 */
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
        alm.requirement.domain.Requirement candidate = requirements.findById(id).orElseThrow(() -> missing(id));
        alm.requirement.domain.Requirement current = requirements.lockById(id).orElseThrow(() -> missing(id));
        sk.workingset.domain.LifecycleResult decision = current.delete2(LifecycleCommand.locked("permanent delete"));
        if (!decision.succeeded()) {
            throw DomainException.withMessage(FailureReason.DELETED, id.value());
        }
        requirements.purge(current);
        eventPublisher.publishEvent(new CompleteDeleteEvent(id, candidate.trackerId(), Instant.now()));
    }

    private static DomainException missing(LocalId id) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, id.value());
    }
}
