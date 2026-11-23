package com.airedgesoft.sharedkernel.workingset.application.query.provider;


import java.util.List;

/**
 * Active business identities ordered by numeric LocalId (Snowflake).
 */
public interface TrackerItemCatalogQueryProvider {

    /**
     * 是否存在任何 Item，包含软删行；用于 Tracker/绑定删除守卫。
     */
    boolean hasAnyItem(Long trackerId);

    /**
     * Full active identity list for a tracker.
     */
    List<Identity> identities(Long trackerId);

    /**
     * Identities needed by compare, including soft-deleted variants that merge can restore.
     */
    default List<Identity> identitiesForMerge(Long trackerId) {
        return identities(trackerId);
    }

    record Identity(Long localId, Long commonId, Integer revision) {
    }
}
