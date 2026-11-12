package alm.requirement.domain;

import java.util.Objects;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.TrackerItem;
import sk.workingset.domain.Version;

/**
 * ALM Requirement 聚合（首切片最小模型，P5 字段）。
 *
 * <p>用户 2026-11-11 裁定：
 * <ul>
 *   <li>{@code LocalId} 是所有业务对象聚合的唯一标识，因此 {@code RequirementId = LocalId}；</li>
 *   <li>{@code version} 是发布大版本（字符串，可为空），只有某个版次发布时才出现；</li>
 *   <li>{@code revision} 是手动触发的递增版次，普通修改不升级；</li>
 *   <li>{@code lockVersion} 是乐观锁，数据变动后自动 +1，不需要调用方显式触发。</li>
 * </ul>
 *
 * <p>实现 SK 的公共领域接口 {@link TrackerItem}（T4 §3.1 两层接口并存）。
 * 首切片不包含状态机、审批流、评论、附件与复杂属性模型。
 */
public final class Requirement implements TrackerItem {

    private final LocalId localId;
    private final CommonId commonId;
    private final TrackerId trackerId;
    private final String title;
    private final String description;
    private final Revision revision;
    private final Version version;
    private final LockVersion lockVersion;

    private Requirement(LocalId localId,
                        CommonId commonId,
                        TrackerId trackerId,
                        String title,
                        String description,
                        Revision revision,
                        Version version,
                        LockVersion lockVersion) {
        this.localId = localId;
        this.commonId = commonId;
        this.trackerId = trackerId;
        this.title = title;
        this.description = description;
        this.revision = revision;
        this.version = version;
        this.lockVersion = lockVersion;
    }

    /** 创建尚未发布大版本的 Requirement；乐观锁从 0 开始。 */
    public static Requirement create(LocalId localId,
                                     CommonId commonId,
                                     TrackerId trackerId,
                                     String title,
                                     String description,
                                     Revision revision) {
        return create(localId, commonId, trackerId, title, description, revision, Version.none());
    }

    /** 创建带发布大版本的 Requirement；乐观锁从 0 开始。 */
    public static Requirement create(LocalId localId,
                                     CommonId commonId,
                                     TrackerId trackerId,
                                     String title,
                                     String description,
                                     Revision revision,
                                     Version version) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(version, "version");
        return new Requirement(
                localId, commonId, trackerId, title, description, revision, version, LockVersion.initial());
    }

    /** 从持久化重建聚合（保留发布大版本与乐观锁，不用当前值冒充）。 */
    public static Requirement restore(LocalId localId,
                                      CommonId commonId,
                                      TrackerId trackerId,
                                      String title,
                                      String description,
                                      Revision revision,
                                      Version version,
                                      LockVersion lockVersion) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(lockVersion, "lockVersion");
        return new Requirement(
                localId, commonId, trackerId, title, description, revision, version, lockVersion);
    }

    @Override
    public LocalId localId() {
        return localId;
    }

    /**
     * 普通修改标题（不可变聚合）：返回新实例；乐观锁自动 +1，**不升级业务 {@code revision}**，
     * **不改变发布大版本 {@code version}**。
     *
     * @param expectedLockVersion 调用方读取到的乐观锁版本；不一致时显式冲突，不静默覆盖
     * @throws LockVersionConflictException 乐观锁已变化
     */
    public Requirement changeTitle(String newTitle, LockVersion expectedLockVersion) {
        Objects.requireNonNull(newTitle, "title");
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, newTitle, description, revision, version,
                lockVersion.next());
    }

    public Requirement upgradeRevision(LockVersion expectedLockVersion) {
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, title, description,
                Revision.of(Math.incrementExact(revision.value())), version, lockVersion.next());
    }

    private void verifyLockVersion(LockVersion expectedLockVersion) {
        Objects.requireNonNull(expectedLockVersion, "expectedLockVersion");
        if (!lockVersion.equals(expectedLockVersion)) {
            throw new LockVersionConflictException(expectedLockVersion, lockVersion);
        }
    }

    @Override
    public CommonId commonId() {
        return commonId;
    }

    @Override
    public Revision revision() {
        return revision;
    }

    public TrackerId trackerId() {
        return trackerId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** 发布大版本；{@link Version#none()} 表示尚未发布。 */
    public Version version() {
        return version;
    }

    /** 乐观锁版本。 */
    public LockVersion lockVersion() {
        return lockVersion;
    }
}
