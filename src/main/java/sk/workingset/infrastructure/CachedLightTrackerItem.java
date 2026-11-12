package sk.workingset.infrastructure;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * LightTrackerItem 的 Redis 缓存 DTO；只缓存聚合元数据，不缓存业务内容。
 */
public record CachedLightTrackerItem(String localId,
                                     String commonId,
                                     String trackerId,
                                     String workingSetId,
                                     int revision,
                                     String sourceLocalId,
                                     int sourceRevision,
                                     boolean materialized) {

    public static CachedLightTrackerItem from(LightTrackerItem item) {
        return new CachedLightTrackerItem(
                item.localId().value(),
                item.commonId().value(),
                item.trackerId().value(),
                item.workingSetId().value(),
                item.revision().value(),
                item.sourceLocalId().value(),
                item.sourceRevision().value(),
                item.materialized());
    }

    public LightTrackerItem toDomain() {
        return LightTrackerItem.restore(
                LocalId.of(localId),
                CommonId.of(commonId),
                TrackerId.of(trackerId),
                WorkingSetId.of(workingSetId),
                Revision.of(revision),
                LocalId.of(sourceLocalId),
                Revision.of(sourceRevision),
                materialized);
    }
}
