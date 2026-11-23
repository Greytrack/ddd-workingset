package com.airedgesoft.sharekernel.tracker.domain;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharekernel.workingset.domain.Classification;
import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerDeletedEvent;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import org.jmolecules.ddd.annotation.AggregateRoot;

import java.util.Objects;

/**
 * sourceId 表示直接派生来源，parentId 表示同工作集内的组织父级；
 * isDefault 表示该（工作集，空间，分类）的默认 Tracker。
 *
 * <p>业务原生 Tracker 只能在默认工作集创建；子工作集不允许独立创建原始 Tracker，
 * 只能从直属父工作集纳入 Shared Tracker 或派生非共享 Tracker。名称唯一约束只作用于
 * 业务原生 Tracker，因此实际等价于（产品空间，分类）内唯一。
 */
@AggregateRoot
public final class Tracker {
    private final Long trackerId;
    private final Long commonId;
    private final Long sourceId;
    private final Long workingSetId;
    private final Long productSpaceId;
    private final long classificationId;
    private final long baseClassId;
    private final long classId;
    private final SharingMode sharingMode;
    private final Long parentId;
    private final boolean isDefault;
    private final boolean newAggregate;
    private final Long createdBy;
    private Long updatedBy;
    private String name;
    private boolean deleted;

    public Tracker(Long trackerId, String name, Long sourceId, Long workingSetId,
                   Long productSpaceId, Classification classification, SharingMode sharingMode,
                   Long parentId, boolean isDefault) {
        this(trackerId, trackerId, name, sourceId, workingSetId, productSpaceId,
                classification, sharingMode, parentId, isDefault);
    }

    public Tracker(Long trackerId, String name, Long sourceId, Long workingSetId,
                   Long productSpaceId, Classification classification, SharingMode sharingMode,
                   Long parentId, boolean isDefault, Long actor) {
        this(trackerId, trackerId, name, sourceId, workingSetId, productSpaceId,
                classification, sharingMode, parentId, isDefault, true, actor, actor);
    }

    private Tracker(Long trackerId, Long commonId, String name, Long sourceId,
                    Long workingSetId, Long productSpaceId, Classification classification,
                    SharingMode sharingMode, Long parentId, boolean isDefault) {
        this(trackerId, commonId, name, sourceId, workingSetId, productSpaceId,
                classification, sharingMode, parentId, isDefault, true);
    }

    private Tracker(Long trackerId, Long commonId, String name, Long sourceId,
                    Long workingSetId, Long productSpaceId, Classification classification,
                    SharingMode sharingMode, Long parentId, boolean isDefault, boolean newAggregate) {
        this(trackerId, commonId, name, sourceId, workingSetId, productSpaceId,
                classification.numericId(), classification.baseClassId(), classification.classId(),
                sharingMode, parentId, isDefault, newAggregate, null, null);
    }

    private Tracker(Long trackerId, Long commonId, String name, Long sourceId,
                    Long workingSetId, Long productSpaceId, Classification classification,
                    SharingMode sharingMode, Long parentId, boolean isDefault, boolean newAggregate,
                    Long createdBy, Long updatedBy) {
        this(trackerId, commonId, name, sourceId, workingSetId, productSpaceId,
                classification.numericId(), classification.baseClassId(), classification.classId(),
                sharingMode, parentId, isDefault, newAggregate, createdBy, updatedBy);
    }

    private Tracker(Long trackerId, Long commonId, String name, Long sourceId,
                    Long workingSetId, Long productSpaceId, long classificationId,
                    long baseClassId, long classId,
                    SharingMode sharingMode, Long parentId, boolean isDefault, boolean newAggregate,
                    Long createdBy, Long updatedBy) {
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        if (trackerId <= 0 || commonId <= 0) {
            throw new IllegalArgumentException("Tracker IDs must be positive");
        }
        this.name = name;
        this.sourceId = sourceId;
        this.parentId = parentId;
        this.isDefault = isDefault;
        this.workingSetId = Objects.requireNonNull(workingSetId, "workingSetId");
        this.productSpaceId = Objects.requireNonNull(productSpaceId, "productSpaceId");
        this.classificationId = classificationId;
        this.baseClassId = baseClassId;
        this.classId = classId;
        this.sharingMode = Objects.requireNonNull(sharingMode, "sharingMode");
        this.newAggregate = newAggregate;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        if (sourceId == null && !trackerId.equals(commonId)) {
            throw new IllegalArgumentException("An original Tracker must use its own ID as common ID");
        }
        if (sourceId == null && !WorkingSet.DEFAULT_ID.equals(workingSetId)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "original Tracker must belong to the default Working Set");
        }
        if (sourceId != null && (sharingMode != SharingMode.UNSHARED || WorkingSet.DEFAULT_ID.equals(workingSetId))) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "derived Tracker must be unshared and belong to a child Working Set");
        }
        if (sourceId != null && (trackerId.equals(commonId) || trackerId.equals(sourceId))) {
            throw new IllegalArgumentException("A derived Tracker must have its own ID and a distinct family ID");
        }
    }

    public static Tracker reconstitute(Long id, Long commonId, String name, Long source,
                                       Long owner, Long productSpace, long classificationId,
                                       long baseClassId, long classId,
                                       SharingMode sharing, Long parent, boolean isDefault) {
        return reconstitute(id, commonId, name, source, owner, productSpace, classificationId, baseClassId, classId,
                sharing, parent, isDefault, null, null);
    }

    public static Tracker reconstitute(Long id, Long commonId, String name, Long source,
                                       Long owner, Long productSpace, long classificationId,
                                       long baseClassId, long classId,
                                       SharingMode sharing, Long parent, boolean isDefault,
                                       Long createdBy, Long updatedBy) {
        return new Tracker(id, commonId, name, source, owner, productSpace, classificationId,
                baseClassId, classId, sharing, parent, isDefault, false, createdBy, updatedBy);
    }

    /**
     * 业务原生 Tracker 只能创建在默认工作集；子工作集没有等价的创建入口。
     */
    public static Tracker original(Long id, ProductSpace space, Classification classification, Long parentId) {
        return original(id, space, classification, parentId, false);
    }

    /**
     * 业务原生 Tracker 只能创建在默认工作集；isDefault 仅对默认工作集内的槽位有意义。
     */
    public static Tracker original(Long id, ProductSpace space, Classification classification, Long parentId,
                                   boolean isDefault) {
        return original(id, space, classification, parentId, isDefault, null);
    }

    public static Tracker original(Long id, ProductSpace space, Classification classification, Long parentId,
                                   boolean isDefault, Long actor) {
        return new Tracker(id, id, isDefault ? classification.name() : null, null, WorkingSet.DEFAULT_ID, space.numericId(), classification,
                classification.sharingMode(), parentId, isDefault, true, actor, actor);
    }

    public static Tracker original(Long id, String name, ProductSpace space, Classification classification,
                                   SharingMode sharingMode) {
        return original(id, name, space, classification, sharingMode, null);
    }

    public static Tracker original(Long id, String name, ProductSpace space, Classification classification,
                                   SharingMode sharingMode, Long actor) {
        String normalizedName = normalizeName(name);
        if (!classification.allows(sharingMode)) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, classification.id());
        }
        return new Tracker(id, id, normalizedName, null, WorkingSet.DEFAULT_ID, space.numericId(), classification,
                sharingMode, null, false, true, actor, actor);
    }

    public static Tracker derived(Long id, Tracker source, Long target) {
        return derived(id, source, target, false);
    }

    public static Tracker derived(Long id, Tracker source, Long target, boolean isDefault) {
        return derived(id, source, target, isDefault, null);
    }

    public static Tracker derived(Long id, Tracker source, Long target, boolean isDefault,
                                  Long createdBy) {
        return new Tracker(id, source.commonId(), source.name(), source.trackerId(), target,
                source.numericProductSpaceId(), source.classificationId, source.baseClassId, source.classId,
                source.sharingMode(), null,
                isDefault, true, createdBy, createdBy);
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 255) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must contain 1 to 255 characters");
        }
        return normalized;
    }

    /**
     * 记录本次业务操作者；写侧仓储直接使用该审计事实，不再读取请求线程上下文。
     */
    public void touch(Long actor) {
        this.updatedBy = Objects.requireNonNull(actor, "actor");
    }

    /**
     * 软删除：标记 {@code deleted} 并返回已删除事实；调用方随后通过仓储 save 持久化。
     */
    public TrackerDeletedEvent delete(Long actor) {
        if (deleted) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE,
                    "Tracker already deleted: " + trackerId);
        }
        this.deleted = true;
        this.updatedBy = Objects.requireNonNull(actor, "actor");
        return new TrackerDeletedEvent(trackerId);
    }

    public boolean deleted() {
        return deleted;
    }

    public boolean isNew() {
        return newAggregate;
    }

    public Long createdBy() {
        return createdBy;
    }

    public Long updatedBy() {
        return updatedBy;
    }

    public Long trackerId() {
        return trackerId;
    }

    public Long commonId() {
        return commonId;
    }

    public String name() {
        return name;
    }

    public Long sourceId() {
        return sourceId;
    }

    public Long workingSetId() {
        return workingSetId;
    }

    public Long numericProductSpaceId() {
        return productSpaceId;
    }

    public long baseClassId() {
        return baseClassId;
    }

    public long classId() {
        return classId;
    }

    public Long numericClassificationId() {
        return classificationId;
    }

    public SharingMode sharingMode() {
        return sharingMode;
    }

    public Long parentId() {
        return parentId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean rename(String newName) {
        String normalized = normalizeName(newName);
        if (normalized.equals(name)) {
            return false;
        }
        name = normalized;
        return true;
    }

    public String productSpaceId() {
        return productSpaceId.toString();
    }

    public String classificationId() {
        return Long.toString(classificationId);
    }

    public void requireDeletable(boolean hasItems, boolean hasOrgChildren, boolean hasDerivedChildren) {
        if (hasItems) {
            throw DomainException.withMessage(BindingResCode.TRACKER_HAS_ITEMS, Long.toString(trackerId));
        }
        if (hasOrgChildren) {
            throw DomainException.withMessage(BindingResCode.TRACKER_HAS_ORG_CHILDREN, Long.toString(trackerId));
        }
        if (hasDerivedChildren) {
            throw DomainException.withMessage(BindingResCode.TRACKER_HAS_DERIVED_CHILDREN, Long.toString(trackerId));
        }
    }

    public boolean isDerived() {
        return sourceId != null;
    }
}
