package com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import lombok.Getter;
import org.jmolecules.ddd.annotation.AggregateRoot;

import java.util.*;

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
@Getter
@AggregateRoot
public final class LightTrackerItem {

    private static final Set<Long> INITIALLY_CHECKED_IN_CLASS_IDS = Set.of(140101L);

    private final Long localId;
    private final Long commonId;
    private final Long trackerId;
    private final Long workingSetId;
    private final long productSpaceId;
    private final long classificationId;
    private final String createdBy;
    private Integer revision;
    private Long sourceLocalId;
    private Integer sourceRevision;
    private boolean materialized;
    private boolean deleted;
    private InitialIndexStatus initialIndexStatus;
    private EffectiveIndexStatus upgradeIndexStatus;
    private EffectiveIndexStatus checkinIndexStatus;
    private DowngradeIndexStatus downgradeIndexStatus;
    private final Map<Integer, LightTrackerItemRevisionEntity> revisions = new LinkedHashMap<>();

    public void applyCheckin(int checkedInRevision, String effectiveFlags) {
        requireCurrentRevision(checkedInRevision);
        if (!materialized) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Check-in requires local content");
        }
        validateEffectiveFlags(effectiveFlags);
        checkinIndexStatus = new EffectiveIndexStatus(checkedInRevision, 0, effectiveFlags,
                effectiveFlags.charAt(0) == '1', effectiveFlags.charAt(1) == '1',
                effectiveFlags.charAt(2) == '1', effectiveFlags.charAt(3) == '1', effectiveFlags.charAt(4) == '1');
        upgradeIndexStatus = null;
        downgradeIndexStatus = null;
    }

    public static LightTrackerItem restoreRevisionState(LightTrackerItem item, List<Integer> memberFlags) {
        Iterator<Integer> flags = memberFlags.iterator();
        item.revisions.replaceAll((revision, member) -> new LightTrackerItemRevisionEntity(revision, flags.next()));
        return item;
    }

    public boolean applyCheckout(int before, int after) {
        if (after != (long) before + 1) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Expected a one-step checkout upgrade");
        }
        if (revision == after) {
            return false;
        }
        requireCurrentRevision(before);
        if (!materialized) {
            switchToLocalBacked(before);
        }
        advanceRevision(after);
        return true;
    }

    public boolean applyCheckoutCancellation(int before, int after, String effectiveFlags) {
        if (after <= 0 || before - 1 != after) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Expected a one-step checkout cancellation");
        }
        if (revision == after) {
            return false;
        }
        requireCurrentRevision(before);
        downgradeRevision(before, after, effectiveFlags);
        return true;
    }

    private void requireCurrentRevision(int expectedRevision) {
        if (revision != expectedRevision) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Expected revision is not current");
        }
    }

    private static void validateEffectiveFlags(String effectiveFlags) {
        if (effectiveFlags == null || !effectiveFlags.matches("[01]{10}")) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "Expected ten binary effective flags");
        }
    }

    private void downgradeRevision(int before, int after, String effectiveFlags) {
        if (!materialized || !revisions.containsKey(after)) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Cannot roll back to the requested revision");
        }
        validateEffectiveFlags(effectiveFlags);
        String restoredFlags = effectiveFlags.substring(0, 2) + "1" + effectiveFlags.substring(3);
        checkinIndexStatus = null;
        revisions.remove(before);
        revision = after;
        sourceRevision = after;
        upgradeIndexStatus = null;
        downgradeIndexStatus = new DowngradeIndexStatus(before, after, 0, restoredFlags,
                restoredFlags.charAt(0) == '1', restoredFlags.charAt(1) == '1', true);
        checkInvariants();
    }


    private static long knownClassId(long classificationId) {
        return classificationId == 14010101L || classificationId == 14010102L ? 140101L : 0L;
    }

    private LightTrackerItem(Long localId,
                             Long commonId,
                             Long trackerId,
                             Long workingSetId,
                             long productSpaceId,
                             long classificationId,
                             Integer revision,
                             Long sourceLocalId,
                             Integer sourceRevision,
                             boolean materialized,
                             String createdBy) {
        if (localId <= 0 || commonId <= 0 || trackerId <= 0 || workingSetId <= 0 || sourceLocalId <= 0) {
            throw new IllegalArgumentException("LightTrackerItem IDs must be positive");
        }
        if (productSpaceId <= 0 || classificationId <= 0) {
            throw new IllegalArgumentException("product space and classification IDs must be positive");
        }
        if (revision < 0 || sourceRevision < 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "revision must not be negative");
        }
        this.localId = localId;
        this.commonId = commonId;
        this.trackerId = trackerId;
        this.workingSetId = workingSetId;
        this.productSpaceId = productSpaceId;
        this.classificationId = classificationId;
        this.revision = revision;
        this.revisions.put(revision, new LightTrackerItemRevisionEntity(revision, 0));
        this.sourceLocalId = sourceLocalId;
        this.sourceRevision = sourceRevision;
        this.materialized = materialized;
        this.createdBy = createdBy;
        this.initialIndexStatus = InitialIndexStatus.forClass(knownClassId(classificationId));
    }

    /**
     * 派生创建：预分配 {@code localId}（由调用方生成）、继承 {@code commonId}、
     * 记录来源与初始业务版次，初始为 Source-backed。
     */
    public static LightTrackerItem derive(Long localId, Long commonId, Long trackerId,
                                          Long workingSetId, Integer revision,
                                          Long sourceLocalId, Integer sourceRevision,
                                          long numericProductSpaceId, long classificationId) {
        return derive(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                numericProductSpaceId, classificationId, null);
    }

    public static LightTrackerItem derive(Long localId, Long commonId, Long trackerId,
                                          Long workingSetId, Integer revision,
                                          Long sourceLocalId, Integer sourceRevision,
                                          long numericProductSpaceId, long classificationId, String createdBy) {
        return derive(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                numericProductSpaceId, classificationId, knownClassId(classificationId), createdBy);
    }

    public static LightTrackerItem derive(Long localId, Long commonId, Long trackerId,
                                          Long workingSetId, Integer revision, Long sourceLocalId,
                                          Integer sourceRevision, long numericProductSpaceId,
                                          long classificationId, long classId, String createdBy) {
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
                localId, commonId, trackerId, workingSetId, numericProductSpaceId, classificationId,
                revision, sourceLocalId, sourceRevision, false, createdBy);
        item.initialIndexStatus = InitialIndexStatus.forClass(classId);
        item.checkInvariants();
        return item;
    }

    public static LightTrackerItem local(Long id, Long commonId, Long trackerId,
                                         Long workingSetId, Integer revision,
                                         long numericProductSpaceId, long classificationId) {
        return local(id, commonId, trackerId, workingSetId, revision, numericProductSpaceId, classificationId, null);
    }

    public static LightTrackerItem local(Long id, Long commonId, Long trackerId,
                                         Long workingSetId, Integer revision,
                                         long numericProductSpaceId, long classificationId, String createdBy) {
        return restore(id, commonId, trackerId, workingSetId, revision, id, revision, true,
                numericProductSpaceId, classificationId, createdBy);
    }

    public static LightTrackerItem local(Long id, Long commonId, Long trackerId,
                                         Long workingSetId, Integer revision, long numericProductSpaceId,
                                         long classificationId, long classId, String createdBy) {
        LightTrackerItem item = local(id, commonId, trackerId, workingSetId, revision,
                numericProductSpaceId, classificationId, createdBy);
        item.initialIndexStatus = InitialIndexStatus.forClass(classId);
        return item;
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
                                           long numericProductSpaceId, long classificationId) {
        return restore(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                materialized, numericProductSpaceId, classificationId, null);
    }

    public static LightTrackerItem restore(Long localId,
                                           Long commonId,
                                           Long trackerId,
                                           Long workingSetId,
                                           Integer revision,
                                           Long sourceLocalId,
                                           Integer sourceRevision,
                                           boolean materialized,
                                           long numericProductSpaceId, long classificationId,
                                           String createdBy) {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        LightTrackerItem item = new LightTrackerItem(
                localId, commonId, trackerId, workingSetId, numericProductSpaceId, classificationId,
                revision, sourceLocalId, sourceRevision, materialized, createdBy);
        item.checkInvariants();
        return item;
    }

    public static LightTrackerItem restore(Long localId, Long commonId, Long trackerId,
                                           Long workingSetId, Integer revision, Long sourceLocalId,
                                           Integer sourceRevision, boolean materialized, boolean deleted,
                                           List<Integer> indexedRevisions,
                                           long numericProductSpaceId, long classificationId) {
        return restore(localId, commonId, trackerId, workingSetId, revision, sourceLocalId, sourceRevision,
                materialized, deleted, indexedRevisions, numericProductSpaceId, classificationId, null);
    }

    public static LightTrackerItem restore(Long localId, Long commonId, Long trackerId,
                                           Long workingSetId, Integer revision, Long sourceLocalId,
                                           Integer sourceRevision, boolean materialized, boolean deleted,
                                           List<Integer> indexedRevisions,
                                           long numericProductSpaceId, long classificationId,
                                           String createdBy) {
        LightTrackerItem item = restore(localId, commonId, trackerId, workingSetId, revision,
                sourceLocalId, sourceRevision, materialized, numericProductSpaceId, classificationId, createdBy);
        item.deleted = deleted;
        item.revisions.clear();
        for (Integer indexed : indexedRevisions) {
            item.revisions.put(indexed, new LightTrackerItemRevisionEntity(indexed, null));
        }
        if (!item.revisions.containsKey(revision)) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Current revision is missing from the index");
        }
        return item;
    }

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
    private void advanceRevision(int after) {
        checkinIndexStatus = null;
        revisions.put(after, new LightTrackerItemRevisionEntity(after, 0));
        revision = after;
        sourceRevision = after;
        downgradeIndexStatus = null;
        upgradeIndexStatus = new EffectiveIndexStatus(after, 1, "0010000000", false, false, true, false, false);
        checkInvariants();
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
    public long productSpaceId() {
        return productSpaceId;
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

    public EffectiveIndexStatus checkinIndexStatus() {
        return checkinIndexStatus;
    }

    public DowngradeIndexStatus downgradeIndexStatus() {
        return downgradeIndexStatus;
    }


    public EffectiveIndexStatus upgradeIndexStatus() {
        return upgradeIndexStatus;
    }

    public InitialIndexStatus initialIndexStatus() {
        return initialIndexStatus;
    }

    public record InitialIndexStatus(int effectiveCheckedOut, String effectiveFlags, boolean flag5) {
        private static InitialIndexStatus forClass(long classId) {
            return INITIALLY_CHECKED_IN_CLASS_IDS.contains(classId)
                    ? new InitialIndexStatus(0, "1110000000", false)
                    : new InitialIndexStatus(1, "1110100000", true);
        }
    }

    public record EffectiveIndexStatus(int effectiveRevision, int effectiveCheckedOut, String effectiveFlags,
                                       boolean flag1, boolean flag2, boolean flag3, boolean flag4, boolean flag5) {
    }

    public record DowngradeIndexStatus(int removedRevision, int effectiveRevision, int effectiveCheckedOut,
                                       String effectiveFlags, boolean flag1, boolean flag2, boolean flag3) {
    }
}
