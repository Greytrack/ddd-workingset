package sk.workingset.domain.event;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSetId;

/**
 * A business BC has created a native Tracker Item in its owning Working Set.
 */
public record TrackerItemCreatedEvent(LocalId localId, CommonId commonId, TrackerId trackerId,
                                      WorkingSetId workingSetId, Revision revision) {
}
