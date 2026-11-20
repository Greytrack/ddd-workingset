package alm.requirement.application.eventhandler;

import alm.requirement.application.command.RequirementMaterializationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.dto.MaterializeRequest;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.domain.event.TrackerItemMaterializationCompletedEvent;
import sk.workingset.domain.event.TrackerItemMaterializationCompletedEvent.Outcome;
import sk.workingset.domain.event.TrackerItemMaterializationRequestedEvent;

/** ALM 监听 SK 的实例化请求，在自己的事务中保存 Requirement 并返回完成事件。 */
@Component
public class RequirementMaterializationRequestedListener {
    private final RequirementMaterializationService materialization;
    private final ApplicationEventPublisher events;

    public RequirementMaterializationRequestedListener(RequirementMaterializationService materialization,
                                                       ApplicationEventPublisher events) {
        this.materialization = materialization;
        this.events = events;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementMaterializationRequestedListener.on(sk.workingset.domain.event.TrackerItemMaterializationRequestedEvent)")
    public void on(TrackerItemMaterializationRequestedEvent event) {
        try {
            materialization.materialize(request(event), target(event));
            events.publishEvent(new TrackerItemMaterializationCompletedEvent(event.eventId(),
                    event.targetLocalId(), Outcome.MATERIALIZED, event.targetLocalId(),
                    event.targetRevision(), event.beforeLockVersion(), event.afterLockVersion(), null));
        } catch (RuntimeException failure) {
            events.publishEvent(new TrackerItemMaterializationCompletedEvent(event.eventId(),
                    event.targetLocalId(), Outcome.FAILED, null, null, event.beforeLockVersion(), event.afterLockVersion(), failure.getMessage()));
        }
    }

    private static MaterializeRequest request(TrackerItemMaterializationRequestedEvent event) {
        return new MaterializeRequest(event.targetLocalId(), event.targetTrackerId(), event.commonId(),
                event.sourceLocalId(), event.beforeRevision(), event.afterRevision(),
                event.beforeLockVersion(), event.afterLockVersion(), event.targetRevision(),
                event.eventId(), event.snapshotRef());
    }

    private static TargetIdentity target(TrackerItemMaterializationRequestedEvent event) {
        return new TargetIdentity(event.targetLocalId(), event.targetTrackerId(), event.commonId(),
                event.targetRevision(), false);
    }
}
