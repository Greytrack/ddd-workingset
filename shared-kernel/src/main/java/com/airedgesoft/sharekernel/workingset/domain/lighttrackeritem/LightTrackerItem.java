package com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import org.jmolecules.ddd.annotation.AggregateRoot;

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

    private final Long localId;
    private final Long commonId;
    private final Long trackerId;
    private final Long workingSetId;
    private final long numericProductSpaceId;
    private final long classificationId;
    private final Map<Integer, LightTrackerItemRevisionEntity> revisions = new LinkedHashMap<>();
    private final String createdBy;
    private Integer revision;
    private Long sourceLocalId;
    private Integer sourceRevision;
    private boolean materialized;
    private boolean deleted;

    private LightTrackerItem(Long localId,
                             Long commonId,
                             Long trackerId,
                             Long workingSetId,
                             ItemClassification classification,
                             Integer revision,
                             Long sourceLocalId,
                             Integer sourceRevision,
                             boolean materialized,
                             String createdBy) {
        if (localId <= 0 || commonId <= 0 || trackerId <= 0 || workingSetId <= 0 || sourceLocalId <= 0) {
            throw new IllegalArgumentException("LightTrackerItem IDs must be positive");
        }
        if (revision < 0 || sourceRevision < 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "revision must not be negative");
        }
        this.localId = localId;
        this.commonId = commonId;
        this.trackerId = trackerId;
        this.workingSetId = workingSetId;
        this.numericProductSpaceId = classification.numericProductSpaceId();
        this.classificationId = classification.classificationId();
        this.revision = revision;
        this.revisions.put(revision, new LightTrackerItemRevisionEntity(revision));
        this.sourceLocalId = sourceLocalId;
        this.sourceRevision = sourceRevision;
        this.materialized = materialized;
        this.createdBy = createdBy;
    }

    /**
     * 派生创建：预分配 {@code localId}（由调用方生成）、继承 {@code commonId}、
     * 记录来源与初始业务版次，初始为 Source-backed。
     */
    public static LightTrackerItem derive(Long localId, Long commonId, Long trackerId,
                                          Long workingSetId, Integer revision,
                                          Long sourceLocalId, Integer sourceRevision,
                                          ItemClassification classification) {
        return derive(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                classification, null);
    }

    public static LightTrackerItem derive(Long localId, Long commonId, Long trackerId,
                                          Long workingSetId, Integer revision,
                                          Long sourceLocalId, Integer sourceRevision,
                                          ItemClassification classification, String createdBy) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(classification, "classification");
        if (localId.equals(sourceLocalId)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST,
                    "derived variant must be source-backed: localId must differ from sourceLocalId");
        }
        LightTrackerItem item = new LightTrackerItem(
                localId, commonId, trackerId, workingSetId, classification,
                revision, sourceLocalId, sourceRevision, false, createdBy);
        item.checkInvariants();
        return item;
    }

    public static LightTrackerItem local(Long id, Long commonId, Long trackerId,
                                         Long workingSetId, Integer revision,
                                         ItemClassification classification) {
        return local(id, commonId, trackerId, workingSetId, revision, classification, null);
    }

    public static LightTrackerItem local(Long id, Long commonId, Long trackerId,
                                         Long workingSetId, Integer revision,
                                         ItemClassification classification, String createdBy) {
        return restore(id, commonId, trackerId, workingSetId, revision, id, revision, true, classification, createdBy);
    }

    /**
     * 从持久化/事件恢复时重建（调用方必须保证字段一致）。
     */
    public static LightTrackerItem restore(Long localId,
                                           Long commonId,
                                           Long trackerId,
                                           Long workingSetId,
                                           Integer revision,
                                           Long sourceLocalId,
                                           Integer sourceRevision,
                                           boolean materialized,
                                           ItemClassification classification) {
        return restore(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                materialized, classification, null);
    }

    public static LightTrackerItem restore(Long localId,
                                           Long commonId,
                                           Long trackerId,
                                           Long workingSetId,
                                           Integer revision,
                                           Long sourceLocalId,
                                           Integer sourceRevision,
                                           boolean materialized,
                                           ItemClassification classification,
                                           String createdBy) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(classification, "classification");
        LightTrackerItem item = new LightTrackerItem(
                localId, commonId, trackerId, workingSetId, classification,
                revision, sourceLocalId, sourceRevision, materialized, createdBy);
        item.checkInvariants();
        return item;
    }

    public static LightTrackerItem restore(Long localId, Long commonId, Long trackerId,
                                           Long workingSetId, Integer revision, Long sourceLocalId,
                                           Integer sourceRevision, boolean materialized, boolean deleted,
                                           List<Integer> indexedRevisions, ItemClassification classification) {
        return restore(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                materialized, deleted, indexedRevisions, classification, null);
    }

    public static LightTrackerItem restore(Long localId, Long commonId, Long trackerId,
                                           Long workingSetId, Integer revision, Long sourceLocalId,
                                           Integer sourceRevision, boolean materialized, boolean deleted,
                                           List<Integer> indexedRevisions, ItemClassification classification,
                                           String createdBy) {
        LightTrackerItem item = restore(localId, commonId, trackerId, workingSetId, revision,
                sourceLocalId, sourceRevision, materialized, classification, createdBy);
        item.deleted = deleted;
        item.revisions.clear();
        for (Integer indexed : indexedRevisions) {
            item.revisions.put(indexed, new LightTrackerItemRevisionEntity(indexed));
        }
        if (!item.revisions.containsKey(revision)) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Current revision is missing from the index");
        }
        return item;
    }

    /**
     * 首次 COW 生效：在单次聚合状态变更中同步更新三字段（T3 §6、A2）。
     *
     * @param expectedRevision 目标业务版次；必须等于本变体当前 revision（普通修改不升级版次）
     */
    public void switchToLocalBacked(Integer expectedRevision) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        if (materialized) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "already materialized: localId=" + Long.toString(localId));
        }
        if (!revision.equals(expectedRevision)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST,
                    "revision mismatch: expected " + revision + " but got " + expectedRevision);
        }
        this.materialized = true;
        this.sourceLocalId = localId;
        this.sourceRevision = revision;
        checkInvariants();
    }

    public List<LightTrackerItemRevisionEntity> revisions() {
        return List.copyOf(revisions.values());
    }

    public LightTrackerItemRevisionEntity currentRevisionIndex() {
        return revisions.get(revision);
    }

    /**
     * Apply a committed explicit upgrade of this item's own business revision.
     */
    public boolean advanceRevision(Integer before, Integer after) {
        if (after != (long) before + 1) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Expected a one-step revision upgrade");
        }
        if (revision >= after) {
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

    public Long localId() {
        return localId;
    }

    public Long commonId() {
        return commonId;
    }

    public Long trackerId() {
        return trackerId;
    }

    public Long workingSetId() {
        return workingSetId;
    }

    /**
     * 所属 Tracker 的产品空间 ID。
     */
    public long numericProductSpaceId() {
        return numericProductSpaceId;
    }

    /**
     * 所属子分类 ID（Tracker 的 {@code tracker_class}/{@code classificationId}）。
     */
    public long classificationId() {
        return classificationId;
    }

    public Integer revision() {
        return revision;
    }

    public Long sourceLocalId() {
        return sourceLocalId;
    }

    public Integer sourceRevision() {
        return sourceRevision;
    }

    /**
     * Synchronize the committed lifecycle of this variant without changing its content source or revision.
     */
    /**
     * 软删除：标记 deleted；已删除时返回 false。
     */
    public boolean delete() {
        if (deleted) {
            return false;
        }
        this.deleted = true;
        return true;
    }

    /**
     * 恢复：清除 deleted；未删除时返回 false。
     */
    public boolean undelete() {
        if (!deleted) {
            return false;
        }
        this.deleted = false;
        return true;
    }

    public boolean deleted() {
        return deleted;
    }

    public boolean materialized() {
        return materialized;
    }

    /**
     * 创建该变体的业务操作者；由应用层在构造聚合时写入，写侧仓储直接使用。
     */
    public String createdBy() {
        return createdBy;
    }

    /**
     * 有效内容来源：Source-backed 时为外部来源变体，Local-backed 时为自身。
     */
    public Long effectiveContentSource() {
        return materialized ? localId : sourceLocalId;
    }

    /**
     * 读取有效内容应使用的业务版次。
     */
    public Integer effectiveContentRevision() {
        return materialized ? revision : sourceRevision;
    }
}
