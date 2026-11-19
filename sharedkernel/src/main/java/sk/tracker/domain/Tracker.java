package sk.tracker.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import sk.common.error.DomainException;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
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
    private String name;
    private final TrackerId sourceId;
    private final WorkingSetId workingSetId;
    private final Long numericProductSpaceId;
    private final Long numericClassificationId;
    private final SharingMode sharingMode;
    private final TrackerId parentId;
    private final boolean isDefault;
    private final TrackerCompatibility compatibility;

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

    public Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                   Long numericProductSpaceId, Long numericClassificationId, SharingMode sharingMode,
                   TrackerId parentId, boolean isDefault) {
        this(trackerId, trackerId, name, sourceId, workingSetId, numericProductSpaceId,
                numericClassificationId, sharingMode, parentId, isDefault);
    }

    private Tracker(TrackerId trackerId, TrackerId commonId, String name, TrackerId sourceId,
                   WorkingSetId workingSetId, Long numericProductSpaceId, Long numericClassificationId,
                   SharingMode sharingMode, TrackerId parentId, boolean isDefault) {
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        this.name = name;
        this.sourceId = sourceId;
        this.parentId = parentId;
        this.isDefault = isDefault;
        this.workingSetId = Objects.requireNonNull(workingSetId, "workingSetId");
        this.numericProductSpaceId = Objects.requireNonNull(numericProductSpaceId, "productSpaceId");
        this.numericClassificationId = Objects.requireNonNull(numericClassificationId, "classificationId");
        this.sharingMode = Objects.requireNonNull(sharingMode, "sharingMode");
        this.compatibility = new TrackerCompatibility(trackerId, numericClassificationId);
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
                                       WorkingSetId owner, Long productSpace, Long classification,
                                       SharingMode sharing, TrackerId parent, boolean isDefault) {
        return new Tracker(id, commonId, name, source, owner, productSpace, classification, sharing, parent, isDefault);
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
        String normalizedName = normalizeName(name);
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
        return new Tracker(id, source.commonId(), source.name(), source.trackerId(), target,
                source.numericProductSpaceId(), source.numericClassificationId(), source.sharingMode(), null, isDefault);
    }

    public TrackerId trackerId() { return trackerId; }
    public TrackerId commonId() { return commonId; }
    public String name() { return name; }
    public TrackerId sourceId() { return sourceId; }
    public WorkingSetId workingSetId() { return workingSetId; }
    public Long numericProductSpaceId() { return numericProductSpaceId; }
    public Long numericClassificationId() { return numericClassificationId; }
    public SharingMode sharingMode() { return sharingMode; }
    public TrackerId parentId() { return parentId; }
    public boolean isDefault() { return isDefault; }
    public TrackerCompatibility compatibility() { return compatibility; }

    public boolean rename(String newName) {
        String normalized = normalizeName(newName);
        if (normalized.equals(name)) {
            return false;
        }
        name = normalized;
        return true;
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 255) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must contain 1 to 255 characters");
        }
        return normalized;
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
