package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.event.MergeTargetEvent;
import sk.workingset.domain.event.TrackerItemCreatedEvent;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;

/**
 * SK 内部的 LTI 生命周期处理服务。
 *
 * <p>业务 BC 不再通过同步端口请求 LTI 切换；继承编辑完成与合并目标物化都由事件驱动。
 * 永久删除后的 LTI 清理由业务对象删除事件驱动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackerItemLifecycleService {

    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;
    private final BusinessTrackerItems businessItems;


    @Transactional
    public void completeInheritedEdit(LocalId localId, Revision revision) {
        switchToLocalContent(localId, revision);
    }

    @Transactional
    public void applyCommittedUpgrade(LocalId id, Revision before, Revision after) {
        LightTrackerItem item = items.findByIdForUpdate(id)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "item index missing for committed revision: " + id.value()));
        if (!item.materialized()) {
            item.switchToLocalBacked(before);
        }
        if (item.advanceRevision(before, after)) {
            items.save(item);
        }
    }

    @Transactional
    public void removePurged(LocalId localId) {
        items.remove(localId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCreated(TrackerItemCreatedEvent event) {
        // Match delete2's lock order so a late creation event cannot restore a purged index.
        trackers.lockTracker(event.trackerId());
        if (!businessItems.lockContentSource(event.localId()) || items.findById(event.localId()).isPresent()) {
            return;
        }
        items.save(LightTrackerItem.local(event.localId(), event.commonId(), event.trackerId(),
                event.workingSetId(), event.revision()));
    }

    @Transactional
    public void applyMergeTarget(MergeTargetEvent target) {
        if (target.inherited()) {
            switchToLocalContent(target.localId(), target.revision());
            return;
        }
        if (target.create()) {
            items.save(LightTrackerItem.local(target.localId(), target.commonId(), target.trackerId(),
                    target.workingSetId(), target.revision()));
        }
    }

    private void switchToLocalContent(LocalId localId, Revision revision) {
        LightTrackerItem item = items.findById(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "target variant disappeared: " + localId.value()));
        if (!item.materialized()) {
            item.switchToLocalBacked(revision);
            items.save(item);
        }
    }
}
