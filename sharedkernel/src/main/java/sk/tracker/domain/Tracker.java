package sk.tracker.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.BindingResCode;
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
public final class Tracker {
    private final TrackerId trackerId;
    private final TrackerId commonId;
    private final TrackerId sourceId;
    private final WorkingSetId workingSetId;
    private final Long numericProductSpaceId;
    private final Classification classification;
    private final SharingMode sharingMode;
    private final TrackerId parentId;
    private final boolean isDefault;
    private final TrackerCompatibility compatibility;
    private final boolean newAggregate;
    private final boolean initiallyVisible;
    private final Long createdBy;
    private String name;

    public Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                   Long numericProductSpaceId, Classification classification, SharingMode sharingMode,
                   TrackerId parentId, boolean isDefault) {
        this(trackerId, trackerId, name, sourceId, workingSetId, numericProductSpaceId,
                classification, sharingMode, parentId, isDefault);
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
        this(trackerId, commonId, name, sourceId, workingSetId, numericProductSpaceId, classification,
                sharingMode, parentId, isDefault, newAggregate, true, null);
    }

    private Tracker(TrackerId trackerId, TrackerId commonId, String name, TrackerId sourceId,
                    WorkingSetId workingSetId, Long numericProductSpaceId, Classification classification,
                    SharingMode sharingMode, TrackerId parentId, boolean isDefault, boolean newAggregate,
                    boolean initiallyVisible, Long createdBy) {
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        this.name = name;
        this.sourceId = sourceId;
        this.parentId = parentId;
        this.isDefault = isDefault;
        this.workingSetId = Objects.requireNonNull(workingSetId, "workingSetId");
        this.numericProductSpaceId = Objects.requireNonNull(numericProductSpaceId, "productSpaceId");
        this.classification = Objects.requireNonNull(classification, "classification");
        this.sharingMode = Objects.requireNonNull(sharingMode, "sharingMode");
        this.compatibility = new TrackerCompatibility(trackerId, classification.numericId());
        this.newAggregate = newAggregate;
        this.initiallyVisible = initiallyVisible;
        this.createdBy = createdBy;
        if (sourceId == null && !trackerId.equals(commonId)) {
            throw new IllegalArgumentException("An original Tracker must use its own ID as common ID");
        }
        if (sourceId == null && !WorkingSet.DEFAULT_ID.equals(workingSetId) && !isFolderTracker()) {
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
                                       WorkingSetId owner, Long productSpace, Classification classification,
                                       SharingMode sharing, TrackerId parent, boolean isDefault) {
        return new Tracker(id, commonId, name, source, owner, productSpace, classification, sharing, parent, isDefault, false);
    }

    public static Tracker folderTracker(TrackerId id, WorkingSetId owner, ProductSpace space, Classification classification) {
        if (!classification.folder()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "Folder Tracker requires a folder classification");
        }
        return new Tracker(id, id, classification.name(), null, owner, space.numericId(), classification,
                SharingMode.UNSHARED, null, true);
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
        return new Tracker(id, id, null, null, WorkingSet.DEFAULT_ID, space.numericId(), classification,
                classification.sharingMode(), parentId, isDefault);
    }

    public static Tracker original(TrackerId id, String name, ProductSpace space, Classification classification,
                                   SharingMode sharingMode) {
        String normalizedName = normalizeName(name);
        if (!classification.allows(sharingMode)) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, classification.id());
        }
        return new Tracker(id, id, normalizedName, null, WorkingSet.DEFAULT_ID, space.numericId(), classification,
                sharingMode, null, false);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target) {
        return derived(id, source, target, false);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target, boolean isDefault) {
        return derived(id, source, target, isDefault, true, null);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target, boolean isDefault,
                                  boolean initiallyVisible, Long createdBy) {
        return new Tracker(id, source.commonId(), source.name(), source.trackerId(), target,
                source.numericProductSpaceId(), source.classification(), source.sharingMode(), null,
                isDefault, true, initiallyVisible, createdBy);
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 255) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must contain 1 to 255 characters");
        }
        return normalized;
    }

    public boolean isNew() {
        return newAggregate;
    }

    public boolean isInitiallyVisible() {
        return initiallyVisible;
    }

    public Long createdBy() {
        return createdBy;
    }

    public boolean isFolderTracker() {
        return isFolder() && parentId == null && sharingMode == SharingMode.UNSHARED;
    }

    public boolean isFolder() {
        return classification.folder();
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

    public Classification classification() {
        return classification;
    }

    public Long numericClassificationId() {
        return classification.numericId();
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
        return classification.id();
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
