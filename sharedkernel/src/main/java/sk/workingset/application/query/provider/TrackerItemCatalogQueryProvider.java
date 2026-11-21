package sk.workingset.application.query.provider;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

import java.util.List;

/**
 * Active business identities ordered by numeric LocalId (Snowflake).
 */
public interface TrackerItemCatalogQueryProvider {

    /**
     * 是否存在任何 Item，包含软删行；用于 Tracker/绑定删除守卫。
     */
    boolean hasAnyItem(TrackerId trackerId);

    List<Identity> page(TrackerId trackerId, String afterLocalId, int limit);

    /**
     * Full active identity list for a tracker.
     */
    List<Identity> identities(TrackerId trackerId);

    /**
     * Identities needed by compare, including soft-deleted variants that merge can restore.
     */
    default List<Identity> identitiesForMerge(TrackerId trackerId) {
        return identities(trackerId);
    }

    boolean hasActiveFamily(TrackerId trackerId, CommonId commonId);

    record Identity(LocalId localId, CommonId commonId, Revision revision) {
    }
}
