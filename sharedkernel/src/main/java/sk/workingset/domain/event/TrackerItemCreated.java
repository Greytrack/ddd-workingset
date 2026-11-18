package sk.workingset.domain.event;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** A business BC has created a native Tracker Item in its owning Working Set. */
public record TrackerItemCreated(LocalId localId, CommonId commonId, TrackerId trackerId,
                                 WorkingSetId workingSetId, Revision revision) {}
