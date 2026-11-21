package alm.requirement.application.command;

import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.domain.RequirementSnapshotStore;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.RequestThreadHolder;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.Version;
import sk.workingset.domain.event.BusinessObjectDeleteEvent;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.event.SnapshotRefEvent;
import sk.workingset.domain.event.TrackerItemCreatedEvent;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;

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
            throw DomainException.withMessage(FailureReason.DELETED, requirement.localId().value());
        }
    }

    /**
     * 创建并提交一个 Requirement；服务端按工作集分配身份。
     *
     * <p>默认工作集：同一个雪花 ID 同时作为 LocalId 与 CommonId；
     * 非默认工作集：服务端生成两个不相等的雪花 ID。派生不经过本方法，CommonId 沿用来源。
     * 显式指定非默认工作集时，非共享 Tracker 必须属于该工作集；Tracker 不存在时交由持久化层给出冲突。
     */
    @Transactional
    public Requirement create(TrackerId trackerId,
                              WorkingSetId requestedWorkingSetId,
                              String title,
                              String description,
                              Revision revision) {
        if (requestedWorkingSetId != null) {
            requireWorkingSet(requestedWorkingSetId);
        }
        Optional<Tracker> tracker = trackers.findById(trackerId);
        tracker.map(Tracker::workingSetId)
                .filter(owner -> !owner.equals(requestedWorkingSetId))
                .ifPresent(this::requireWorkingSet);
        if (requestedWorkingSetId != null) {
            tracker.ifPresent(current -> {
                if (current.sharingMode() == SharingMode.UNSHARED
                        && !current.workingSetId().equals(requestedWorkingSetId)) {
                    throw DomainException.withMessage(FailureReason.NOT_INCLUDED,
                            "tracker " + trackerId.value() + " is not included in working set "
                                    + requestedWorkingSetId.value());
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
                LocalId.of(String.valueOf(local)), CommonId.of(String.valueOf(common)),
                trackerId, title, description, revision, Version.none(), RequestThreadHolder.getUserId());
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
    public Requirement changeTitle(LocalId localId, String newTitle, LockVersion expectedLockVersion) {
        return edit(localId, expectedLockVersion, current -> current.changeTitle(newTitle, expectedLockVersion));
    }

    /**
     * 普通修改标题和描述；业务 revision 不升级，仍按乐观锁写入并发布来源事件。
     */
    @Transactional
    public Requirement changeContent(LocalId localId, String newTitle, String newDescription,
                                     LockVersion expectedLockVersion) {
        return edit(localId, expectedLockVersion,
                current -> current.changeContent(newTitle, newDescription, expectedLockVersion));
    }

    private Requirement edit(LocalId localId, LockVersion expectedLockVersion, UnaryOperator<Requirement> change) {
        Optional<LightTrackerItem> inherited = items.findById(localId)
                .filter(item -> !item.materialized());
        Requirement current = inherited.map(target -> {
            requireWorkingSet(target.workingSetId());
            return repository.findById(localId).orElseGet(() -> createEditBase(target, expectedLockVersion));
        }).orElseGet(() -> loadWritable(localId));
        requireActive(current);
        return saveChange(current, change.apply(current));
    }

    private void requireWorkingSet(WorkingSetId id) {
        workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
    }

    void requireTrackerWorkingSet(TrackerId trackerId) {
        Tracker tracker = trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.TRACKER_NOT_FOUND, trackerId.value()));
        requireWorkingSet(tracker.workingSetId());
    }

    private Requirement createEditBase(LightTrackerItem target, LockVersion expectedLockVersion) {
        if (!LockVersion.initial().equals(expectedLockVersion)) {
            throw new LockVersionConflictException(expectedLockVersion, LockVersion.initial());
        }
        Requirement base = rebuildInherited(target);
        repository.save(base);
        return base;
    }

    private Requirement rebuildInherited(LightTrackerItem target) {
        Requirement source = load(target.sourceLocalId());
        requireActive(source);
        if (!source.revision().equals(target.sourceRevision())) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, target.sourceLocalId().value());
        }
        return Requirement.create(target.localId(), target.commonId(), target.trackerId(),
                source.title(), source.description(), target.revision(), source.version(), RequestThreadHolder.getUserId());
    }

    /**
     * 显式升级业务版次，触发依赖变体的 COW。
     */
    @Transactional
    public Requirement upgradeRevision(LocalId localId, LockVersion expectedLockVersion) {
        Requirement current = loadWritable(localId);
        requireActive(current);
        return saveChange(current, current.upgradeRevision(expectedLockVersion));
    }

    /**
     * 软删除需求。
     */
    @Transactional
    public void delete(LocalId id) {
        Requirement current = loadWritable(id);
        requireActive(current);
        saveChange(current, current.delete(current.lockVersion()));
    }

    /**
     * 恢复软删除的需求。
     */
    @Transactional
    public void undelete(LocalId id) {
        Requirement current = loadWritable(id);
        saveChange(current, current.undelete(current.lockVersion()));
    }

    private Requirement load(LocalId localId) {
        Objects.requireNonNull(localId, "localId");
        return repository.findById(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "requirement not found: " + localId.value()));
    }

    private Requirement loadWritable(LocalId localId) {
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
}

