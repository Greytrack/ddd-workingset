package com.airedgesoft.sharekernel.workingset.domain.event;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;

/**
 * A business BC has created a native Tracker Item in its owning Working Set.
 */
public record TrackerItemCreatedEvent(LocalId localId, CommonId commonId, TrackerId trackerId,
                                      WorkingSetId workingSetId, Revision revision) {
}
