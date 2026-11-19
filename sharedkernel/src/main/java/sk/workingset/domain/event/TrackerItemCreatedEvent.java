package sk.workingset.domain.event;

import sk.workingset.domain.*;

/**
 * A business BC has created a native Tracker Item in its owning Working Set.
 */
public record TrackerItemCreatedEvent(LocalId localId, CommonId commonId, TrackerId trackerId,
                                      WorkingSetId workingSetId, Revision revision) {
}
