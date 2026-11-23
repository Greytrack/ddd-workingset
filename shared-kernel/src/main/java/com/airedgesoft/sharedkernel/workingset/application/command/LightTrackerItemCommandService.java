package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.RequestThreadHolder;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectProvider;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.port.TrackerItemSourceLock;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeTargetEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectCheckedInEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectCheckedOutEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectCheckoutCancelledEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemCreatedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
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
public class LightTrackerItemCommandService {

    private final TrackerRepository trackerRepository;
    private final LightTrackerItemRepository lightTrackerItemRepository;
    private final LiteBusinessObjectProvider businessObjects;
    private final TrackerItemSourceLock sourceLock;

    /**
     * 继承编辑完成：把未物化的目标变体切换为 Local-backed。
     */
    @Transactional
    public void completeInheritedEdit(Long localId, Integer revision) {
        switchToLocalContent(localId, revision);
    }

    @Transactional
    public void applyCheckout(BusinessObjectCheckedOutEvent event) {
        LightTrackerItem item = lockItemForRevisionChange(event.localId());
        if (item.applyCheckout(event.beforeRevision(), event.afterRevision(), event.effectiveFlags())) {
            lightTrackerItemRepository.save(item);
        }
    }

    @Transactional
    public void applyCheckoutCancellation(BusinessObjectCheckoutCancelledEvent event) {
        LightTrackerItem item = lockItemForRevisionChange(event.localId());
        if (item.applyCheckoutCancellation(event.beforeRevision(),
                event.afterRevision(), event.effectiveFlags())) {
            lightTrackerItemRepository.save(item);
        }
    }

    @Transactional
    public void applyCheckin(BusinessObjectCheckedInEvent event) {
        LightTrackerItem item = lockItemForRevisionChange(event.localId());
        item.applyCheckin(event.revision(), event.effectiveFlags());
        lightTrackerItemRepository.save(item);
    }

    private LightTrackerItem lockItemForRevisionChange(Long localId) {
        return lightTrackerItemRepository.findByIdForUpdate(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "item index missing for committed revision: " + localId));
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
        registerLocal(event.localId(), event.commonId(), event.trackerId(), event.workingSetId(), event.revision(), event.effectiveFlags());
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
            registerLocal(target.localId(), target.commonId(), target.trackerId(), target.workingSetId(), target.revision(), null);
        }
    }

    private void registerLocal(Long id, Long commonId, Long trackerId, Long workingSetId,
                               Integer revision, String effectiveFlags) {
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
                tracker.productSpaceId(), tracker.numericClassificationId(), tracker.classId(), RequestThreadHolder.getUserId());
        if (effectiveFlags != null) {
            item.initializeEffectiveFlags(effectiveFlags);
        }
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
