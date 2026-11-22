package com.airedgesoft.sharekernel.tracker.domain;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharekernel.workingset.domain.Classification;
import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;
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
    private final TrackerId trackerId;
    private final TrackerId commonId;
    private final TrackerId sourceId;
    private final WorkingSetId workingSetId;
    private final Long numericProductSpaceId;
    private final long classificationId;
    private final long baseClassId;
    private final long classId;
    private final SharingMode sharingMode;
    private final TrackerId parentId;
    private final boolean isDefault;
    private final TrackerCompatibility compatibility;
    private final boolean newAggregate;
    private final Long createdBy;
    private Long updatedBy;
    private String name;

    public Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                   Long numericProductSpaceId, Classification classification, SharingMode sharingMode,
                   TrackerId parentId, boolean isDefault) {
        this(trackerId, trackerId, name, sourceId, workingSetId, numericProductSpaceId,
                classification, sharingMode, parentId, isDefault);
    }

    public Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                   Long numericProductSpaceId, Classification classification, SharingMode sharingMode,
                   TrackerId parentId, boolean isDefault, Long actor) {
        this(trackerId, trackerId, name, sourceId, workingSetId, numericProductSpaceId,
                classification, sharingMode, parentId, isDefault, true, actor, actor);
    }

    private Tracker(TrackerId trackerId, TrackerId commonId, String name, TrackerId sourceId,
                    WorkingSetId workingSetId, Long numericProductSpaceId, Classification classification,
                    SharingMode sharingMode, TrackerId parentId, boolean isDefault) {
        this(trackerId, commonId, name, sourceId, workingSetId, numericProductSpaceId,
                classification, sharingMode, parentId, isDefault, true);
    }

    private Tracker(TrackerId trackerId, TrackerId commonId, String name, TrackerId sourceId,
                    WorkingSetId workingSetId, Long numericProductSpaceId, Classification classification,
                    SharingMode sharingMode, TrackerId parentId, boolean isDefault, boolean newAggregate) {
        this(trackerId, commonId, name, sourceId, workingSetId, numericProductSpaceId,
                classification.numericId(), classification.baseClassId(), classification.classId(),
                sharingMode, parentId, isDefault, newAggregate, null, null);
    }

    private Tracker(TrackerId trackerId, TrackerId commonId, String name, TrackerId sourceId,
                    WorkingSetId workingSetId, Long numericProductSpaceId, Classification classification,
                    SharingMode sharingMode, TrackerId parentId, boolean isDefault, boolean newAggregate,
                    Long createdBy, Long updatedBy) {
        this(trackerId, commonId, name, sourceId, workingSetId, numericProductSpaceId,
                classification.numericId(), classification.baseClassId(), classification.classId(),
                sharingMode, parentId, isDefault, newAggregate, createdBy, updatedBy);
    }

    private Tracker(TrackerId trackerId, TrackerId commonId, String name, TrackerId sourceId,
                    WorkingSetId workingSetId, Long numericProductSpaceId, long classificationId,
                    long baseClassId, long classId,
                    SharingMode sharingMode, TrackerId parentId, boolean isDefault, boolean newAggregate,
                    Long createdBy, Long updatedBy) {
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        this.name = name;
        this.sourceId = sourceId;
        this.parentId = parentId;
        this.isDefault = isDefault;
        this.workingSetId = Objects.requireNonNull(workingSetId, "workingSetId");
        this.numericProductSpaceId = Objects.requireNonNull(numericProductSpaceId, "productSpaceId");
        this.classificationId = classificationId;
        this.baseClassId = baseClassId;
        this.classId = classId;
        this.sharingMode = Objects.requireNonNull(sharingMode, "sharingMode");
        this.compatibility = new TrackerCompatibility(trackerId, classificationId);
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

    public static Tracker reconstitute(TrackerId id, TrackerId commonId, String name, TrackerId source,
                                       WorkingSetId owner, Long productSpace, long classificationId,
                                       long baseClassId, long classId,
                                       SharingMode sharing, TrackerId parent, boolean isDefault) {
        return reconstitute(id, commonId, name, source, owner, productSpace, classificationId, baseClassId, classId,
                sharing, parent, isDefault, null, null);
    }

    public static Tracker reconstitute(TrackerId id, TrackerId commonId, String name, TrackerId source,
                                       WorkingSetId owner, Long productSpace, long classificationId,
                                       long baseClassId, long classId,
                                       SharingMode sharing, TrackerId parent, boolean isDefault,
                                       Long createdBy, Long updatedBy) {
        return new Tracker(id, commonId, name, source, owner, productSpace, classificationId,
                baseClassId, classId, sharing, parent, isDefault, false, createdBy, updatedBy);
    }

    /**
     * 业务原生 Tracker 只能创建在默认工作集；子工作集没有等价的创建入口。
     */
    public static Tracker original(TrackerId id, ProductSpace space, Classification classification, TrackerId parentId) {
        return original(id, space, classification, parentId, false);
    }

    /**
     * 业务原生 Tracker 只能创建在默认工作集；isDefault 仅对默认工作集内的槽位有意义。
     */
    public static Tracker original(TrackerId id, ProductSpace space, Classification classification, TrackerId parentId,
                                   boolean isDefault) {
        return original(id, space, classification, parentId, isDefault, null);
    }

    public static Tracker original(TrackerId id, ProductSpace space, Classification classification, TrackerId parentId,
                                   boolean isDefault, Long actor) {
        return new Tracker(id, id, isDefault ? classification.name() : null, null, WorkingSet.DEFAULT_ID, space.numericId(), classification,
                classification.sharingMode(), parentId, isDefault, true, actor, actor);
    }

    public static Tracker original(TrackerId id, String name, ProductSpace space, Classification classification,
                                   SharingMode sharingMode) {
        return original(id, name, space, classification, sharingMode, null);
    }

    public static Tracker original(TrackerId id, String name, ProductSpace space, Classification classification,
                                   SharingMode sharingMode, Long actor) {
        String normalizedName = normalizeName(name);
        if (!classification.allows(sharingMode)) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, classification.id());
        }
        return new Tracker(id, id, normalizedName, null, WorkingSet.DEFAULT_ID, space.numericId(), classification,
                sharingMode, null, false, true, actor, actor);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target) {
        return derived(id, source, target, false);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target, boolean isDefault) {
        return derived(id, source, target, isDefault, null);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target, boolean isDefault,
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

    public boolean isNew() {
        return newAggregate;
    }

    public Long createdBy() {
        return createdBy;
    }

    public Long updatedBy() {
        return updatedBy;
    }

    public TrackerId trackerId() {
        return trackerId;
    }

    public TrackerId commonId() {
        return commonId;
    }

    public String name() {
        return name;
    }

    public TrackerId sourceId() {
        return sourceId;
    }

    public WorkingSetId workingSetId() {
        return workingSetId;
    }

    public Long numericProductSpaceId() {
        return numericProductSpaceId;
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

    public TrackerId parentId() {
        return parentId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public TrackerCompatibility compatibility() {
        return compatibility;
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
        return numericProductSpaceId.toString();
    }

    public String classificationId() {
        return Long.toString(classificationId);
    }

    public void requireDeletable(boolean hasItems, boolean hasOrgChildren, boolean hasDerivedChildren) {
        if (hasItems) {
            throw DomainException.withMessage(BindingResCode.TRACKER_HAS_ITEMS, trackerId.value());
        }
        if (hasOrgChildren) {
            throw DomainException.withMessage(BindingResCode.TRACKER_HAS_ORG_CHILDREN, trackerId.value());
        }
        if (hasDerivedChildren) {
            throw DomainException.withMessage(BindingResCode.TRACKER_HAS_DERIVED_CHILDREN, trackerId.value());
        }
    }

    public boolean isDerived() {
        return sourceId != null;
    }
}
