package alm.requirement.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.infrastructure.RequirementSnapshot;
import alm.requirement.infrastructure.SnapshotStore;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/**
 * ALM Requirement 应用服务：创建、普通修改（修改前快照 + 保存成功后发布来源事件）。
 */
public class RequirementApplicationService {

    private final RequirementRepository repository;
    private final SnapshotStore snapshotStore;
    private final IdentifierGenerator identifierGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public RequirementApplicationService(RequirementRepository repository,
                                          SnapshotStore snapshotStore,
                                          IdentifierGenerator identifierGenerator,
                                          ApplicationEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    /** 创建并提交一个 Requirement；乐观锁从 0 开始，尚未发布大版本。 */
    public Requirement create(LocalId localId,
                              CommonId commonId,
                              TrackerId trackerId,
                              String title,
                              String description,
                              Revision revision) {
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
        Objects.requireNonNull(localId, "localId");
        Requirement current = repository.findById(localId)
                .orElseThrow(() -> new IllegalArgumentException("requirement not found: " + localId.value()));

        // 先做乐观锁校验：聚合不可变，失败时 current 不变，也不会留下无主快照
        Requirement changed = current.changeTitle(newTitle, expectedLockVersion);

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
