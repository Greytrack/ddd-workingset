package bootstrap.web;

import sk.workingset.domain.LightTrackerItem;

/** LightTrackerItem 的最小读模型。 */
public record LightTrackerItemView(String localId,
                                   String commonId,
                                   String name,
                                   String trackerId,
                                   String workingSetId,
                                   int revision,
                                   String sourceLocalId,
                                   int sourceRevision,
                                   boolean materialized) {

    public static LightTrackerItemView from(LightTrackerItem item) {
        return from(item, null);
    }

    public static LightTrackerItemView from(LightTrackerItem item, String name) {
        return new LightTrackerItemView(
                item.localId().value(),
                item.commonId().value(),
                name,
                item.trackerId().value(),
                item.workingSetId().value(),
                item.revision().value(),
                item.sourceLocalId().value(),
                item.sourceRevision().value(),
                item.materialized());
    }
}
