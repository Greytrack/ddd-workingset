package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.cow.CowMaterializationStore;

/**
 * COW 请求处理器：来源变化后，为未物化目标发布逐目标实例化请求。
 *
 * <p>业务保存由 ALM 监听请求事件后在自己的事务中完成；SK 只记录收据并等待完成事件，
 * 不再同步调用业务 BC 的写入能力。
 */
@Service
@RequiredArgsConstructor
public final class CowEventHandler {
    private final LightTrackerItemRepository items;
    private final TransactionTemplate transactionTemplate;
    private final CowMaterializationStore store;
    private final ApplicationEventPublisher events;

    private static TrackerItemMaterializationRequestedEvent request(BusinessObjectUpdateEvent event,
                                                                    LightTrackerItem target) {
        return new TrackerItemMaterializationRequestedEvent(event.eventId(), target.localId(),
                target.trackerId(), target.commonId(), event.sourceLocalId(), event.beforeRevision(),
                event.afterRevision(), event.beforeLockVersion(), event.afterLockVersion(),
                target.revision(), event.snapshotRef());
    }

    /**
     * 来源变化后，为每个未物化的 Source-backed 目标发布逐目标物化请求；收据与事件同事务提交。
     */
    public void onTrackerItemChanged(BusinessObjectUpdateEvent event) {
        for (LightTrackerItem target : items.findSourceBackedTargets(event.sourceLocalId(), event.beforeRevision())) {
            if (target.materialized()) {
                continue;
            }
            boolean alreadyHandled = store.findBySource(event.sourceLocalId()).stream()
                    .anyMatch(entry -> entry.targetLocalId().equals(target.localId()));
            if (alreadyHandled) {
                continue;
            }
            // Publish the reliable event in the same transaction as its receipt.
            // A technical publication failure rolls back both so the source event can be replayed.
            transactionTemplate.execute(status -> {
                var receipt = store.markPending(event.eventId(), target.localId(),
                        event.sourceLocalId(), event.beforeRevision());
                if (receipt.status() != CowMaterializationStore.Status.SWITCHED) {
                    events.publishEvent(request(event, target));
                }
                return null;
            });
        }
    }
}
