package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;

import java.util.List;

/**
 * Active business identities ordered by numeric LocalId (Snowflake).
 */
public interface TrackerItemCatalogQueryProvider {

    /**
     * 是否存在任何 Item，包含软删行；用于 Tracker/绑定删除守卫。
     */
    boolean hasAnyItem(TrackerId trackerId);

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

    record Identity(LocalId localId, CommonId commonId, Revision revision) {
    }
}
