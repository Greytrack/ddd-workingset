package alm.requirement.application.command;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import alm.requirement.application.port.RequirementMutationStore;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.event.RequirementPurged;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.LifecycleCommand;
import sk.workingset.domain.LocalId;

/**
 * 协调永久删除与仍依赖该内容的工作集变体。
 *
 * <p>D4 方案 A：Tracker 锁定、COW 依赖判定与 LTI 移除均由 SK 承担（{@link TrackerItemLifecyclePort}）；
 * 本服务只删除业务对象，并在提交后通过 {@link RequirementPurged} 事件通知 SK 清理 LTI。
 */
public class RequirementLifecycleService {
    private final RequirementMutationStore requirements;
    private final TrackerItemLifecyclePort lifecycle;
    private final ApplicationEventPublisher eventPublisher;

    public RequirementLifecycleService(RequirementMutationStore requirements,
                                       TrackerItemLifecyclePort lifecycle,
                                       ApplicationEventPublisher eventPublisher) {
        this.requirements = requirements;
        this.lifecycle = lifecycle;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void delete2(LocalId id) {
        var candidate = requirements.findById(id).orElseThrow(() -> missing(id));
        lifecycle.assertPurgeable(candidate.trackerId(), id);
        var current = requirements.lockById(id).orElseThrow(() -> missing(id));
        var decision = current.delete2(LifecycleCommand.locked("permanent delete"));
        if (!decision.succeeded()) {
            throw new ApplicationFailure(FailureReason.DELETED, id.value());
        }
        requirements.purge(current);
        eventPublisher.publishEvent(new RequirementPurged(id, candidate.trackerId(), Instant.now()));
    }

    private static ApplicationFailure missing(LocalId id) {
        return new ApplicationFailure(FailureReason.NOT_FOUND, id.value());
    }
}
