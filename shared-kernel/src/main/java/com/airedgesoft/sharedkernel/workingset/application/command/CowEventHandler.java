package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowRequestPublications;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.SnapshotRefEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * COW 请求处理器：来源变化后，为未物化目标发布逐目标实例化请求。
 *
 * <p>业务保存由 ALM 监听请求事件后在自己的事务中完成；SK 持久化逐目标请求并等待完成事件，
 * 不再同步调用业务 BC 的写入能力。
 */
@Service
@RequiredArgsConstructor
public class CowEventHandler {
    private final LightTrackerItemRepository items;
    private final TransactionTemplate transactionTemplate;
    private final CowRequestPublications requests;
    private final ApplicationEventPublisher events;

    private static TrackerItemMaterializationRequestedEvent request(BusinessObjectUpdateEvent event,
                                                                    LightTrackerItem target) {
        return new TrackerItemMaterializationRequestedEvent(event.eventId(), target.localId(),
                target.trackerId(), target.commonId(), event.sourceLocalId(), event.beforeRevision(),
                event.afterRevision(), event.beforeLockVersion(), event.afterLockVersion(),
                target.revision(), new SnapshotRefEvent(event.eventId()));
    }

    public void onTrackerItemChanged(BusinessObjectUpdateEvent event) {
        for (LightTrackerItem candidate : items.findSourceBackedTargets(event.sourceLocalId(), event.beforeRevision())) {
            transactionTemplate.executeWithoutResult(status -> publishForTarget(event, candidate.localId()));
        }
    }

    private void publishForTarget(BusinessObjectUpdateEvent event, Long targetId) {
        // Serialize request selection for each target until its publication commits.
        LightTrackerItem target = items.findByIdForUpdate(targetId).orElse(null);
        if (target == null || target.materialized() || !target.sourceLocalId().equals(event.sourceLocalId())
                || !target.sourceRevision().equals(event.beforeRevision())
                || requests.exists(event.sourceLocalId(), targetId)) {
            return;
        }
        events.publishEvent(request(event, target));
    }
}
