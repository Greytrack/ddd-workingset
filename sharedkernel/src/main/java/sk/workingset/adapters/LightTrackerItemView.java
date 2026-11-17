package sk.workingset.adapters;

import sk.workingset.domain.LightTrackerItem;
import sk.workingset.application.port.BusinessTrackerItems.Presentation;

/** LightTrackerItem 的最小读模型。 */
public record LightTrackerItemView(String localId,
                                   String commonId,
                                   String name,
                                   String number,
                                   String trackerId,
                                   String workingSetId,
                                   int revision,
                                   String sourceLocalId,
                                   int sourceRevision,
                                   boolean materialized) {

    public static LightTrackerItemView from(LightTrackerItem item) {
        return from(item, null);
    }

    public static LightTrackerItemView from(LightTrackerItem item, Presentation presentation) {
        return new LightTrackerItemView(
                item.localId().value(),
                item.commonId().value(),
                presentation == null ? null : presentation.name(),
                presentation == null ? null : presentation.number(),
                item.trackerId().value(),
                item.workingSetId().value(),
                item.revision().value(),
                item.sourceLocalId().value(),
                item.sourceRevision().value(),
                item.materialized());
    }
}
