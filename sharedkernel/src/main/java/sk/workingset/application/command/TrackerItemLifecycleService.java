package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sk.RequestThreadHolder;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.TrackerItemSourceLock;
import sk.workingset.application.query.provider.BusinessObjectTypeRegistry;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.event.MergeTargetEvent;
import sk.workingset.domain.event.TrackerItemCreatedEvent;
import sk.workingset.domain.lighttrackeritem.ItemClassification;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSetId;

/**
 * SK 内部的 LTI 生命周期处理服务。
 *
 * <p>业务 BC 不再通过同步端口请求 LTI 切换；继承编辑完成与合并目标物化都由事件驱动。
 * 永久删除后的 LTI 清理由业务对象删除事件驱动。
 */
@Service
@RequiredArgsConstructor
public class TrackerItemLifecycleService {

    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;
    private final BusinessObjectTypeRegistry registry;

    private static ItemClassification classificationOf(Tracker tracker) {
        return new ItemClassification(tracker.numericProductSpaceId(), tracker.numericClassificationId());
    }

    /**
     * 继承编辑完成：把未物化的目标变体切换为 Local-backed。
     */
    @Transactional
    public void completeInheritedEdit(LocalId localId, Revision revision) {
        switchToLocalContent(localId, revision);
    }

    /**
     * 应用已提交的显式升版：必要时先物化，再推进版次并登记新成员。
     */
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

    /**
     * 按当前业务状态同步变体软删标记；当前状态优先于事件到达顺序。
     */
    @Transactional
    public void synchronizeDeletion(LocalId id) {
        LightTrackerItem current = items.findById(id).orElse(null);
        if (current == null) {
            return;
        }
        TrackerItemSourceLock.State state = lockBusinessState(current.trackerId(), id);
        if (state == null) {
            return;
        }
        items.findByIdForUpdate(id).ifPresent(item -> {
            if (item.recordDeletionState(state.deleted())) {
                items.save(item);
            }
        });
    }

    /**
     * 移除已彻底删除变体的索引。
     */
    @Transactional
    public void removePurged(LocalId localId) {
        items.remove(localId);
    }

    /**
     * 幂等登记原生创建产生的 Local-backed LTI；须在调用方事务内执行。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCreated(TrackerItemCreatedEvent event) {
        registerLocal(event.localId(), event.commonId(), event.trackerId(), event.workingSetId(), event.revision());
    }

    /**
     * 按合并目标事件物化继承变体或新建 Local-backed LTI。
     */
    @Transactional
    public void applyMergeTarget(MergeTargetEvent target) {
        if (target.inherited()) {
            switchToLocalContent(target.localId(), target.revision());
            return;
        }
        if (target.create()) {
            registerLocal(target.localId(), target.commonId(), target.trackerId(), target.workingSetId(), target.revision());
        }
    }

    private void registerLocal(LocalId id, CommonId commonId, TrackerId trackerId, WorkingSetId workingSetId,
                               Revision revision) {
        if (items.findById(id).isPresent()) {
            return;
        }
        TrackerItemSourceLock.State state = lockBusinessState(trackerId, id);
        if (state == null || items.findById(id).isPresent()) {
            return;
        }
        Tracker tracker = trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, trackerId.value()));
        LightTrackerItem item = LightTrackerItem.local(id, commonId, trackerId, workingSetId, revision,
                classificationOf(tracker), RequestThreadHolder.getUserId());
        item.recordDeletionState(state.deleted());
        items.save(item);
    }

    private TrackerItemSourceLock.State lockBusinessState(TrackerId trackerId, LocalId id) {
        return registry.forTracker(trackerId).sourceLock().lock(id).orElse(null);
    }

    private void switchToLocalContent(LocalId localId, Revision revision) {
        LightTrackerItem item = items.findByIdForUpdate(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "target variant disappeared: " + localId.value()));
        if (!item.materialized()) {
            item.switchToLocalBacked(revision);
            items.save(item);
        }
    }
}
