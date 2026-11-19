package sk.workingset.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import sk.common.error.DomainException;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.error.WorkingsetResultCode;

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
public record Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                      Long numericProductSpaceId, Long numericClassificationId, SharingMode sharingMode,
                      TrackerId parentId,
                      boolean isDefault) {

    public Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                   String productSpaceId, String classificationId, SharingMode sharingMode, TrackerId parentId,
                   boolean isDefault) {
        this(trackerId, name, sourceId, workingSetId, Long.valueOf(productSpaceId),
                Long.valueOf(classificationId), sharingMode, parentId, isDefault);
    }

    public Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                   String productSpaceId, String classificationId, SharingMode sharingMode, TrackerId parentId) {
        this(trackerId, name, sourceId, workingSetId, productSpaceId, classificationId, sharingMode, parentId, false);
    }

    public Tracker(TrackerId trackerId, TrackerId sourceId, WorkingSetId workingSetId,
                   String productSpaceId, String classificationId, SharingMode sharingMode, TrackerId parentId) {
        this(trackerId, null, sourceId, workingSetId, productSpaceId, classificationId, sharingMode, parentId, false);
    }

    public Tracker {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(numericProductSpaceId, "productSpaceId");
        Objects.requireNonNull(numericClassificationId, "classificationId");
        Objects.requireNonNull(sharingMode, "sharingMode");
        if (sourceId == null && !WorkingSet.DEFAULT_ID.equals(workingSetId)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "original Tracker must belong to the default Working Set");
        }
        if (sourceId != null && (sharingMode != SharingMode.UNSHARED || WorkingSet.DEFAULT_ID.equals(workingSetId))) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "derived Tracker must be unshared and belong to a child Working Set");
        }
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
        return new Tracker(id, null, null, WorkingSet.DEFAULT_ID, space.id(), classification.id(),
                classification.sharingMode(), parentId, isDefault);
    }

    public static Tracker original(TrackerId id, String name, ProductSpace space, Classification classification,
                                   SharingMode sharingMode) {
        String normalizedName = name == null ? "" : name.strip();
        if (normalizedName.isEmpty() || normalizedName.codePointCount(0, normalizedName.length()) > 255) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must contain 1 to 255 characters");
        }
        if (!classification.allows(sharingMode)) {
            throw new BindingFailure(BindingFailure.Reason.SharingModeMismatch, classification.id());
        }
        return new Tracker(id, normalizedName, null, WorkingSet.DEFAULT_ID, space.id(), classification.id(),
                sharingMode, null, false);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target) {
        return derived(id, source, target, false);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target, boolean isDefault) {
        return new Tracker(id, source.name(), source.trackerId(), target, source.productSpaceId(),
                source.classificationId(), source.sharingMode(), null, isDefault);
    }

    public String productSpaceId() {
        return numericProductSpaceId.toString();
    }

    public String classificationId() {
        return numericClassificationId.toString();
    }

    public void requireDeletable(boolean hasItems, boolean hasOrgChildren, boolean hasDerivedChildren) {
        if (hasItems) {
            throw new BindingFailure(BindingFailure.Reason.TrackerHasItems, trackerId.value());
        }
        if (hasOrgChildren) {
            throw new BindingFailure(BindingFailure.Reason.TrackerHasOrgChildren, trackerId.value());
        }
        if (hasDerivedChildren) {
            throw new BindingFailure(BindingFailure.Reason.TrackerHasDerivedChildren, trackerId.value());
        }
    }

    public boolean isDerived() {
        return sourceId != null;
    }
}
