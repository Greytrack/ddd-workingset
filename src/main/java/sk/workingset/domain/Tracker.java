package sk.workingset.domain;

import java.util.Objects;

import sk.workingset.domain.Classification.SharingMode;

/** sourceId 表示直接派生来源，parentId 表示同工作集内的组织父级。name 仅用于界面可读展示。 */
public record Tracker(TrackerId trackerId, String name, TrackerId sourceId, WorkingSetId workingSetId,
                      String productSpaceId, String classificationId, SharingMode sharingMode, TrackerId parentId) {

    public Tracker(TrackerId trackerId, TrackerId sourceId, WorkingSetId workingSetId,
                   String productSpaceId, String classificationId, SharingMode sharingMode, TrackerId parentId) {
        this(trackerId, null, sourceId, workingSetId, productSpaceId, classificationId, sharingMode, parentId);
    }

    public Tracker {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(productSpaceId, "productSpaceId");
        Objects.requireNonNull(classificationId, "classificationId");
        Objects.requireNonNull(sharingMode, "sharingMode");
        if (sourceId == null && !WorkingSet.DEFAULT_ID.equals(workingSetId)) {
            throw new IllegalArgumentException("original Tracker must belong to the default Working Set");
        }
        if (sourceId != null && (sharingMode != SharingMode.UNSHARED || WorkingSet.DEFAULT_ID.equals(workingSetId))) {
            throw new IllegalArgumentException("derived Tracker must be unshared and belong to a child Working Set");
        }
    }

    public static Tracker original(TrackerId id, ProductSpace space, Classification classification, TrackerId parentId) {
        return new Tracker(id, null, WorkingSet.DEFAULT_ID, space.id(), classification.id(),
                classification.sharingMode(), parentId);
    }

    public static Tracker derived(TrackerId id, Tracker source, WorkingSetId target) {
        return new Tracker(id, source.name(), source.trackerId(), target, source.productSpaceId(),
                source.classificationId(), source.sharingMode(), null);
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
