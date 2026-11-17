package sk.workingset.application.port;

import java.util.List;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/** Active business identities ordered by LocalId in UTF-8 byte order. */
public interface TrackerItemCatalog {
    List<Identity> page(TrackerId trackerId, String afterLocalId, int limit);
    boolean hasActiveFamily(TrackerId trackerId, CommonId commonId);

    record Identity(LocalId localId, CommonId commonId, Revision revision) {}
}
