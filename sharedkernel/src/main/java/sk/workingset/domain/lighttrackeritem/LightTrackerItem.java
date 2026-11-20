package sk.workingset.domain.lighttrackeritem;

import org.jmolecules.ddd.annotation.AggregateRoot;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.error.WorkingsetResultCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 首次物化原子切换三字段；显式升版同步推进本地来源版次与成员索引。
 */
@AggregateRoot
public final class LightTrackerItem {

    private final LocalId localId;
    private final CommonId commonId;
    private final TrackerId trackerId;
    private final WorkingSetId workingSetId;
    private Revision revision;
    private final Map<Revision, LightTrackerItemRevisionEntity> revisions = new LinkedHashMap<>();

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
        this.revisions.put(revision, new LightTrackerItemRevisionEntity(revision));
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
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST,
                    "derived variant must be source-backed: localId must differ from sourceLocalId");
        }
        LightTrackerItem item = new LightTrackerItem(
                localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision, false);
        item.checkInvariants();
        return item;
    }

    public static LightTrackerItem local(LocalId id, CommonId commonId, TrackerId trackerId,
                                         WorkingSetId workingSetId, Revision revision) {
        return restore(id, commonId, trackerId, workingSetId, revision, id, revision, true);
    }

    /**
     * 从持久化/事件恢复时重建（调用方必须保证字段一致）。
     */
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
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "already materialized: localId=" + localId.value());
        }
        if (!revision.equals(expectedRevision)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST,
                    "revision mismatch: expected " + revision.value() + " but got " + expectedRevision.value());
        }
        this.materialized = true;
        this.sourceLocalId = localId;
        this.sourceRevision = revision;
        checkInvariants();
    }

    public static LightTrackerItem restore(LocalId localId, CommonId commonId, TrackerId trackerId,
                                           WorkingSetId workingSetId, Revision revision, LocalId sourceLocalId,
                                           Revision sourceRevision, boolean materialized, List<Revision> indexedRevisions) {
        LightTrackerItem item = restore(localId, commonId, trackerId, workingSetId, revision,
                sourceLocalId, sourceRevision, materialized);
        item.revisions.clear();
        for (Revision indexed : indexedRevisions) {
            item.revisions.put(indexed, new LightTrackerItemRevisionEntity(indexed));
        }
        if (!item.revisions.containsKey(revision)) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Current revision is missing from the index");
        }
        return item;
    }

    public List<LightTrackerItemRevisionEntity> revisions() {
        return List.copyOf(revisions.values());
    }

    public LightTrackerItemRevisionEntity currentRevisionIndex() {
        return revisions.get(revision);
    }

    /** Apply a committed explicit upgrade of this item's own business revision. */
    public boolean advanceRevision(Revision before, Revision after) {
        if (after.value() != (long) before.value() + 1) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Expected a one-step revision upgrade");
        }
        if (revision.value() >= after.value()) {
            return false;
        }
        if (!revision.equals(before) || !materialized) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Revision predecessor is not ready");
        }
        revisions.put(after, new LightTrackerItemRevisionEntity(after));
        revision = after;
        sourceRevision = after;
        checkInvariants();
        return true;
    }

    private void checkInvariants() {
        if (!materialized) {
            if (sourceLocalId.equals(localId)) {
                throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "L1 violated: source-backed variant must not point to itself");
            }
        } else if (!sourceLocalId.equals(localId) || !sourceRevision.equals(revision)) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE,
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

    /**
     * 有效内容来源：Source-backed 时为外部来源变体，Local-backed 时为自身。
     */
    public LocalId effectiveContentSource() {
        return materialized ? localId : sourceLocalId;
    }

    /**
     * 读取有效内容应使用的业务版次。
     */
    public Revision effectiveContentRevision() {
        return materialized ? revision : sourceRevision;
    }
}
