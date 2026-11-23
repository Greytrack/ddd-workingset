package com.airedgesoft.sharekernel.workingset.application.command;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.RequestThreadHolder;
import com.airedgesoft.sharekernel.businessframework.domain.litebusinessobject.LiteBusinessObjectProvider;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.application.port.TrackerItemSourceLock;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeTargetEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemCreatedEvent;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.ItemClassification;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SK 内部的 LTI 生命周期处理服务。
 *
 * <p>业务 BC 不再通过同步端口请求 LTI 切换；继承编辑完成与合并目标物化都由事件驱动。
 * 永久删除后的 LTI 清理由业务对象删除事件驱动。
 */
@Service
@RequiredArgsConstructor
public class TrackerItemCommandService {

    private final TrackerRepository trackerRepository;
    private final LightTrackerItemRepository lightTrackerItemRepository;
    private final LiteBusinessObjectProvider businessObjects;
    private final TrackerItemSourceLock sourceLock;

    private static ItemClassification classificationOf(Tracker tracker) {
        return new ItemClassification(tracker.numericProductSpaceId(), tracker.numericClassificationId());
    }

    /**
     * 继承编辑完成：把未物化的目标变体切换为 Local-backed。
     */
    @Transactional
    public void completeInheritedEdit(Long localId, Integer revision) {
        switchToLocalContent(localId, revision);
    }

    /**
     * 应用已提交的显式升版：必要时先物化，再推进版次并登记新成员。
     */
    @Transactional
    public void applyCommittedUpgrade(Long id, Integer before, Integer after) {
        LightTrackerItem item = lightTrackerItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "item index missing for committed revision: " + id));
        if (!item.materialized()) {
            item.switchToLocalBacked(before);
        }
        if (item.advanceRevision(before, after)) {
            lightTrackerItemRepository.save(item);
        }
    }

    /**
     * 按当前业务状态同步变体软删标记；当前状态优先于事件到达顺序。
     */
    @Transactional
    public void synchronizeDeletion(Long id) {
        lightTrackerItemRepository.findByIdForUpdate(id).ifPresent(item -> {
            businessObjects.findById(id).ifPresent(current -> {
                boolean changed = current.isDeleted() ? item.delete() : item.undelete();
                if (changed) {
                    lightTrackerItemRepository.save(item);
                }
            });
        });
    }

    /**
     * 移除已彻底删除变体的索引。
     */
    @Transactional
    public void removePurged(Long localId) {
        lightTrackerItemRepository.remove(localId);
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

    private void registerLocal(Long id, Long commonId, Long trackerId, Long workingSetId,
                               Integer revision) {
        TrackerItemSourceLock.State current = sourceLock.lock(id).orElse(null);
        if (current == null) {
            return;
        }
        if (lightTrackerItemRepository.findById(id).isPresent()) {
            return;
        }
        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, Long.toString(trackerId)));
        LightTrackerItem item = LightTrackerItem.local(id, commonId, trackerId, workingSetId, revision,
                classificationOf(tracker), RequestThreadHolder.getUserId());
        if (current.deleted()) {
            item.delete();
        }
        lightTrackerItemRepository.save(item);
    }

    private void switchToLocalContent(Long localId, Integer revision) {
        LightTrackerItem item = lightTrackerItemRepository.findByIdForUpdate(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "target variant disappeared: " + localId));
        if (!item.materialized()) {
            item.switchToLocalBacked(revision);
            lightTrackerItemRepository.save(item);
        }
    }
}
