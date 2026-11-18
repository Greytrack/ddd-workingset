package sk.workingset.application.query.provider;

import java.util.List;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/** Active business identities ordered by numeric LocalId (Snowflake). */
public interface TrackerItemCatalog {
    List<Identity> page(TrackerId trackerId, String afterLocalId, int limit);

    /** Full active identity list for a tracker; used by compare/merge. */
    List<Identity> identities(TrackerId trackerId);

    boolean hasActiveFamily(TrackerId trackerId, CommonId commonId);

    record Identity(LocalId localId, CommonId commonId, Revision revision) {}
}
