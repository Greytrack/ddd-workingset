package sk.workingset.domain;

import java.util.Objects;

/**
 * Working Set 专属独立聚合（D-008）：身份、来源与有效变体定位权威。
 *
 * <p>状态契约（T3 K1′/A2）：
 * <ul>
 *   <li>{@code materialized=false} → Source-backed：{@code sourceLocalId} 指向外部实际内容来源，
 *       {@code sourceRevision} 为该来源的业务版次；</li>
 *   <li>{@code materialized=true} → Local-backed：{@code sourceLocalId == localId} 且
 *       {@code sourceRevision == revision}。</li>
 * </ul>
 * 三字段只在 {@link #switchToLocalBacked(Revision)} 中一次更新；不提供单独 setter。
 */
public final class LightTrackerItem {

    private final LocalId localId;
    private final CommonId commonId;
    private final TrackerId trackerId;
    private final WorkingSetId workingSetId;
    private final Revision revision;

    private LocalId sourceLocalId;
    private Revision sourceRevision;
    private boolean materialized;

    private LightTrackerItem(LocalId localId,
                             CommonId commonId,
                             TrackerId trackerId,
                             WorkingSetId workingSetId,
                             Revision revision,
                             LocalId sourceLocalId,
                             Revision sourceRevision,
                             boolean materialized) {
        this.localId = localId;
        this.commonId = commonId;
        this.trackerId = trackerId;
        this.workingSetId = workingSetId;
        this.revision = revision;
        this.sourceLocalId = sourceLocalId;
        this.sourceRevision = sourceRevision;
        this.materialized = materialized;
    }

    /**
     * 派生创建：预分配 {@code localId}（由调用方生成）、继承 {@code commonId}、
     * 记录来源与初始业务版次，初始为 Source-backed。
     */
    public static LightTrackerItem derive(LocalId localId,
                                          CommonId commonId,
                                          TrackerId trackerId,
                                          WorkingSetId workingSetId,
                                          Revision revision,
                                          LocalId sourceLocalId) {
        return derive(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, revision);
    }

    public static LightTrackerItem derive(LocalId localId, CommonId commonId, TrackerId trackerId,
                                          WorkingSetId workingSetId, Revision revision,
                                          LocalId sourceLocalId, Revision sourceRevision) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        if (localId.equals(sourceLocalId)) {
            throw new IllegalArgumentException(
                    "derived variant must be source-backed: localId must differ from sourceLocalId");
        }
        LightTrackerItem item = new LightTrackerItem(
                localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision, false);
        item.checkInvariants();
        return item;
    }

    /** 从持久化/事件恢复时重建（调用方必须保证字段一致）。 */
    public static LightTrackerItem restore(LocalId localId,
                                           CommonId commonId,
                                           TrackerId trackerId,
                                           WorkingSetId workingSetId,
                                           Revision revision,
                                           LocalId sourceLocalId,
                                           Revision sourceRevision,
                                           boolean materialized) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        LightTrackerItem item = new LightTrackerItem(
                localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision, materialized);
        item.checkInvariants();
        return item;
    }

    /**
     * 首次 COW 生效：在单次聚合状态变更中同步更新三字段（T3 §6、A2）。
     *
     * @param expectedRevision 目标业务版次；必须等于本变体当前 revision（普通修改不升级版次）
     */
    public void switchToLocalBacked(Revision expectedRevision) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        if (materialized) {
            throw new IllegalStateException("already materialized: localId=" + localId.value());
        }
        if (!revision.equals(expectedRevision)) {
            throw new IllegalArgumentException(
                    "revision mismatch: expected " + revision.value() + " but got " + expectedRevision.value());
        }
        this.materialized = true;
        this.sourceLocalId = localId;
        this.sourceRevision = revision;
        checkInvariants();
    }

    private void checkInvariants() {
        if (!materialized) {
            if (sourceLocalId.equals(localId)) {
                throw new IllegalStateException("L1 violated: source-backed variant must not point to itself");
            }
        } else if (!sourceLocalId.equals(localId) || !sourceRevision.equals(revision)) {
            throw new IllegalStateException(
                    "L2 violated: local-backed variant must have sourceLocalId=localId and sourceRevision=revision");
        }
    }

    public LocalId localId() {
        return localId;
    }

    public CommonId commonId() {
        return commonId;
    }

    public TrackerId trackerId() {
        return trackerId;
    }

    public WorkingSetId workingSetId() {
        return workingSetId;
    }

    public Revision revision() {
        return revision;
    }

    public LocalId sourceLocalId() {
        return sourceLocalId;
    }

    public Revision sourceRevision() {
        return sourceRevision;
    }

    public boolean materialized() {
        return materialized;
    }

    /** 有效内容来源：Source-backed 时为外部来源变体，Local-backed 时为自身。 */
    public LocalId effectiveContentSource() {
        return materialized ? localId : sourceLocalId;
    }

    /** 读取有效内容应使用的业务版次。 */
    public Revision effectiveContentRevision() {
        return materialized ? revision : sourceRevision;
    }
}
