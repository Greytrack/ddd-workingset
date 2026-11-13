package alm.requirement.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.infrastructure.RequirementSnapshot;
import alm.requirement.infrastructure.SnapshotStore;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.TrackerScopePort;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * ALM Requirement 应用服务：创建、普通修改（修改前快照 + 保存成功后发布来源事件）。
 */
public class RequirementApplicationService {

    private final RequirementRepository repository;
    private final SnapshotStore snapshotStore;
    private final IdentifierGenerator identifierGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final TrackerScopePort trackerScope;

    public RequirementApplicationService(RequirementRepository repository,
                                          SnapshotStore snapshotStore,
                                          IdentifierGenerator identifierGenerator,
                                          ApplicationEventPublisher eventPublisher,
                                          TrackerScopePort trackerScope) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.trackerScope = Objects.requireNonNull(trackerScope, "trackerScope");
    }

    /**
     * 创建并提交一个 Requirement；乐观锁从 0 开始，尚未发布大版本。
     *
     * <p>D10 归属解析：显式指定工作集时，共享 Tracker 的实际归属由 Tracker 自身工作集（默认工作集）
     * 派生、请求可来自任意绑定工作集；非共享 Tracker 必须由它所在的工作集创建。
     * 未指定工作集或 Tracker 不存在时不在此拦截，由持久化层按既有约束给出冲突。
     */
    public Requirement create(LocalId localId,
                              CommonId commonId,
                              TrackerId trackerId,
                              WorkingSetId requestedWorkingSetId,
                              String title,
                              String description,
                              Revision revision) {
        if (requestedWorkingSetId != null) {
            trackerScope.find(trackerId).ifPresent(scope -> {
                if (scope.sharingMode() == SharingMode.UNSHARED
                        && !scope.workingSetId().equals(requestedWorkingSetId)) {
                    throw new ApplicationFailure(FailureReason.NOT_INCLUDED,
                            "tracker " + trackerId.value() + " is not included in working set "
                                    + requestedWorkingSetId.value());
                }
            });
        }
        Requirement requirement = Requirement.create(
                localId, commonId, trackerId, title, description, revision);
        repository.save(requirement);
        return requirement;
    }

    /**
     * 普通修改标题：先以 {@code expectedLockVersion} 判乐观锁（冲突即失败，不产生快照或事件），
     * 再捕获修改前完整快照，保存成功后发布 {@link RequirementChanged}。
     *
     * <p>业务 {@code revision} 不升级；发布大版本 {@code version} 不变；仅乐观锁自动 +1。
     */
    @Transactional
    public Requirement changeTitle(LocalId localId, String newTitle, LockVersion expectedLockVersion) {
        Requirement current = load(localId);
        requireActive(current);
        return saveChange(current, current.changeTitle(newTitle, expectedLockVersion));
    }

    @Transactional
    public Requirement upgradeRevision(LocalId localId, LockVersion expectedLockVersion) {
        Requirement current = load(localId);
        requireActive(current);
        return saveChange(current, current.upgradeRevision(expectedLockVersion));
    }

    @Transactional
    public void delete(LocalId id) {
        Requirement current = load(id);
        requireActive(current);
        saveChange(current, current.delete(current.lockVersion()));
    }

    @Transactional
    public void undelete(LocalId id) {
        Requirement current = load(id);
        saveChange(current, current.undelete(current.lockVersion()));
    }

    private static void requireActive(Requirement requirement) {
        if (requirement.deleted()) {
            throw new ApplicationFailure(FailureReason.DELETED, requirement.localId().value());
        }
    }

    private Requirement load(LocalId localId) {
        Objects.requireNonNull(localId, "localId");
        return repository.findById(localId)
                .orElseThrow(() -> new ApplicationFailure(FailureReason.NOT_FOUND, "requirement not found: " + localId.value()));
    }

    Requirement saveChange(Requirement current, Requirement changed) {
        RequirementSnapshot snapshot = RequirementSnapshot.of(current);
        SnapshotRef snapshotRef = snapshotStore.save(snapshot);

        repository.save(changed);

        RequirementChanged event = new RequirementChanged(
                identifierGenerator.nextId(),
                changed.localId(),
                changed.localId(),
                current.revision(),
                changed.revision(),
                current.lockVersion(),
                changed.lockVersion(),
                snapshotRef,
                Instant.now());

        // 发布记录随来源事务提交，监听器在提交后异步处理。
        eventPublisher.publishEvent(event);
        return changed;
    }
}
