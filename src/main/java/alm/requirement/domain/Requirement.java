package alm.requirement.domain;

import java.util.Objects;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.TrackerItem;
import sk.workingset.domain.Version;

/**
 * ALM Requirement 聚合（首切片最小模型，P5 字段）。
 *
 * <p>用户 2026-11-11 裁定：LocalId 是所有业务对象聚合的唯一标识，因此 {@code RequirementId = LocalId}，
 * 不另建独立 ID。实现 SK 的公共领域接口 {@link TrackerItem}（T4 §3.1 两层接口并存）。
 *
 * <p>首切片不包含状态机、审批流、评论、附件与复杂属性模型。
 */
public final class Requirement implements TrackerItem {

    private final LocalId localId;
    private final CommonId commonId;
    private final TrackerId trackerId;
    private final String title;
    private final String description;
    private final Revision revision;
    private final Version version;

    private Requirement(LocalId localId,
                        CommonId commonId,
                        TrackerId trackerId,
                        String title,
                        String description,
                        Revision revision,
                        Version version) {
        this.localId = localId;
        this.commonId = commonId;
        this.trackerId = trackerId;
        this.title = title;
        this.description = description;
        this.revision = revision;
        this.version = version;
    }

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
        return new Requirement(localId, commonId, trackerId, title, description, revision, version);
    }

    @Override
    public LocalId localId() {
        return localId;
    }

    /**
     * 普通修改标题（不可变聚合）：返回新实例；推进并发 {@code version}，**不升级业务 {@code revision}**。
     *
     * @param expectedVersion 调用方读取到的并发版本；不一致时显式冲突，不静默覆盖
     * @throws VersionConflictException 版本已变化
     */
    public Requirement changeTitle(String newTitle, Version expectedVersion) {
        Objects.requireNonNull(newTitle, "title");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        if (!version.equals(expectedVersion)) {
            throw new VersionConflictException(expectedVersion, version);
        }
        return new Requirement(
                localId,
                commonId,
                trackerId,
                newTitle,
                description,
                revision,
                Version.of(Math.addExact(version.value(), 1L)));
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

    public Version version() {
        return version;
    }
}
