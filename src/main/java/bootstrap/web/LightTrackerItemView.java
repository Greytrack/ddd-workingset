package bootstrap.web;

import sk.workingset.domain.LightTrackerItem;

/** LightTrackerItem 的最小读模型。 */
public record LightTrackerItemView(String localId,
                                   String commonId,
                                   String trackerId,
                                   String workingSetId,
                                   int revision,
                                   String sourceLocalId,
                                   int sourceRevision,
                                   boolean materialized) {

    public static LightTrackerItemView from(LightTrackerItem item) {
        return new LightTrackerItemView(
                item.localId().value(),
                item.commonId().value(),
                item.trackerId().value(),
                item.workingSetId().value(),
                item.revision().value(),
                item.sourceLocalId().value(),
                item.sourceRevision().value(),
                item.materialized());
    }
}
