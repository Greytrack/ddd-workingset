package com.airedgesoft.alm.requirement.application.command;


import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;
import com.airedgesoft.alm.requirement.domain.RequirementSnapshotStore;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.RequestThreadHolder;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectDeleteEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.SnapshotRefEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemCreatedEvent;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * ALM Requirement 命令侧应用服务：创建、普通修改（修改前快照 + 保存成功后发布来源事件）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementCommandService {

    private final RequirementRepository repository;
    private final RequirementSnapshotStore snapshotStore;
    private final IdentifierGenerator identifierGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final TrackerRepository trackers;
    private final WorkingSetRepository workingSets;
    private final LightTrackerItemRepository items;

    private static void requireActive(Requirement requirement) {
        if (requirement.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, Long.toString(requirement.localId()));
        }
    }

    /**
     * 创建并提交一个 Requirement；服务端按工作集分配身份。
     *
     * <p>默认工作集：同一个雪花 ID 同时作为 LocalId 与 CommonId；
     * 非默认工作集：服务端生成两个不相等的雪花 ID。派生不经过本方法，CommonId 沿用来源。
     * 显式指定非默认工作集时，非共享 Tracker 必须属于该工作集；Tracker 不存在时交由持久化层给出冲突。
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Requirement create(Long trackerId,
                              Long requestedWorkingSetId,
                              String title,
                              String description,
                              Integer revision) {
        if (requestedWorkingSetId != null) {
            requireWorkingSet(requestedWorkingSetId);
        }
        Optional<Tracker> tracker = trackers.findById(trackerId);
        if (tracker.isEmpty()) {
            throw DomainException.withMessage(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT,
                    "tracker not found or deleted: " + trackerId);
        }
        tracker.map(Tracker::workingSetId)
                .filter(owner -> !owner.equals(requestedWorkingSetId))
                .ifPresent(this::requireWorkingSet);
        if (requestedWorkingSetId != null) {
            tracker.ifPresent(current -> {
                if (current.sharingMode() == SharingMode.UNSHARED
                        && !current.workingSetId().equals(requestedWorkingSetId)) {
                    throw DomainException.withMessage(FailureReason.NOT_INCLUDED,
                            "tracker " + trackerId + " is not included in working set "
                                    + requestedWorkingSetId);
                }
            });
        }
        boolean defaultWorkingSet = tracker
                .map(current -> current.sharingMode() == SharingMode.SHARED
                        || WorkingSet.DEFAULT_ID.equals(current.workingSetId()))
                .orElse(false);
        long local = identifierGenerator.nextId(null).longValue();
        long common = defaultWorkingSet ? local : nextDistinct(local);
        Requirement requirement = Requirement.create(
                Long.valueOf(String.valueOf(local)), Long.valueOf(String.valueOf(common)),
                trackerId, title, description, revision, null, RequestThreadHolder.getUserId());
        repository.save(requirement);
        tracker.ifPresent(current -> eventPublisher.publishEvent(new TrackerItemCreatedEvent(
                requirement.localId(), requirement.commonId(), trackerId,
                current.workingSetId(), requirement.revision())));
        return requirement;
    }

    private long nextDistinct(long local) {
        long common = identifierGenerator.nextId(null).longValue();
        while (common == local) {
            common = identifierGenerator.nextId(null).longValue();
        }
        return common;
    }

    /**
     * 普通修改标题：先以 {@code expectedLockVersion} 判乐观锁（冲突即失败，不产生快照或事件），
     * 再捕获修改前完整快照，保存成功后发布 {@link BusinessObjectUpdateEvent}。
     *
     * <p>业务 {@code revision} 不升级；发布大版本 {@code version} 不变；仅乐观锁自动 +1。
     */
    @Transactional
    public Requirement changeTitle(Long localId, String newTitle, Long expectedLockVersion) {
        return edit(localId, expectedLockVersion, current -> current.changeTitle(newTitle, expectedLockVersion));
    }

    /**
     * 普通修改标题和描述；业务 revision 不升级，仍按乐观锁写入并发布来源事件。
     */
    @Transactional
    public Requirement changeContent(Long localId, String newTitle, String newDescription,
                                     Long expectedLockVersion) {
        return edit(localId, expectedLockVersion,
                current -> current.changeContent(newTitle, newDescription, expectedLockVersion));
    }

    private Requirement edit(Long localId, Long expectedLockVersion, UnaryOperator<Requirement> change) {
        Optional<LightTrackerItem> inherited = items.findById(localId)
                .filter(item -> !item.materialized());
        Requirement current = inherited.map(target -> {
            requireWorkingSet(target.workingSetId());
            return repository.findById(localId).orElseGet(() -> createEditBase(target, expectedLockVersion));
        }).orElseGet(() -> loadWritable(localId));
        requireActive(current);
        return saveChange(current, change.apply(current));
    }

    private void requireWorkingSet(Long id) {
        workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
    }

    void requireTrackerWorkingSet(Long trackerId) {
        Tracker tracker = trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.TRACKER_NOT_FOUND, Long.toString(trackerId)));
        requireWorkingSet(tracker.workingSetId());
    }

    private Requirement createEditBase(LightTrackerItem target, Long expectedLockVersion) {
        if (!Long.valueOf(0L).equals(expectedLockVersion)) {
            throw new LockVersionConflictException(expectedLockVersion, Long.valueOf(0L));
        }
        Requirement base = rebuildInherited(target);
        repository.save(base);
        return base;
    }

    private Requirement rebuildInherited(LightTrackerItem target) {
        Requirement source = load(target.sourceLocalId());
        requireActive(source);
        if (!source.revision().equals(target.sourceRevision())) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, Long.toString(target.sourceLocalId()));
        }
        return Requirement.create(target.localId(), target.commonId(), target.trackerId(),
                source.title(), source.description(), target.revision(), source.version(), RequestThreadHolder.getUserId());
    }

    /**
     * 显式升级业务版次，触发依赖变体的 COW。
     */
    @Transactional
    public Requirement upgradeRevision(Long localId, Long expectedLockVersion) {
        Requirement current = loadWritable(localId);
        requireActive(current);
        return saveChange(current, current.upgradeRevision(expectedLockVersion));
    }

    /**
     * 软删除需求：先捕获删除前快照，由聚合标记删除并返回事实，随后保存并发布事件。
     */
    @Transactional
    public void delete(Long id) {
        Requirement current = loadWritable(id);
        requireActive(current);
        RequirementSnapshot snapshot = RequirementSnapshot.of(current);
        int beforeRevision = current.revision();
        long beforeLockVersion = current.lockVersion();
        BusinessObjectDeleteEvent deletedEvent = current.delete(current.lockVersion());
        saveDeleted(snapshot, current, beforeRevision, beforeLockVersion, deletedEvent);
    }

    /**
     * 恢复软删除的需求。
     */
    @Transactional
    public void undelete(Long id) {
        Requirement current = loadWritable(id);
        saveChange(current, current.undelete(current.lockVersion()));
    }

    private Requirement load(Long localId) {
        Objects.requireNonNull(localId, "localId");
        return repository.findById(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "requirement not found: " + Long.toString(localId)));
    }

    private Requirement loadWritable(Long localId) {
        Requirement requirement = load(localId);
        requireTrackerWorkingSet(requirement.trackerId());
        return requirement;
    }

    /**
     * 保存变更：先保存修改前快照，再保存对象并发布来源变更事件（及软删事件）。
     */
    public Requirement saveChange(Requirement current, Requirement changed) {
        RequirementSnapshot snapshot = RequirementSnapshot.of(current);
        SnapshotRefEvent snapshotRef = snapshotStore.save(snapshot);

        repository.save(changed);

        BusinessObjectUpdateEvent event = new BusinessObjectUpdateEvent(
                identifierGenerator.nextId(null).longValue(),
                changed.localId(),
                changed.localId(),
                current.revision(),
                changed.revision(),
                current.lockVersion(),
                changed.lockVersion(),
                snapshotRef,
                System.currentTimeMillis());

        // 发布记录随来源事务提交，监听器在提交后异步处理。
        eventPublisher.publishEvent(event);
        if (current.deleted() != changed.deleted()) {
            eventPublisher.publishEvent(new BusinessObjectDeleteEvent(changed.localId(), changed.deleted()));
        }
        return changed;
    }

    /**
     * 保存软删除：快照为删除前状态；删除事实由聚合的 delete 返回，随来源事务一并发布。
     */
    public void saveDeleted(RequirementSnapshot snapshot, Requirement changed,
                            int beforeRevision, long beforeLockVersion,
                            BusinessObjectDeleteEvent deletedEvent) {
        SnapshotRefEvent snapshotRef = snapshotStore.save(snapshot);
        repository.save(changed);
        eventPublisher.publishEvent(new BusinessObjectUpdateEvent(
                identifierGenerator.nextId(null).longValue(),
                changed.localId(),
                changed.localId(),
                beforeRevision,
                changed.revision(),
                beforeLockVersion,
                changed.lockVersion(),
                snapshotRef,
                System.currentTimeMillis()));
        eventPublisher.publishEvent(deletedEvent);
    }
}

