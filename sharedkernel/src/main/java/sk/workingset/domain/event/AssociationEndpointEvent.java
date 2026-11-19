package sk.workingset.domain.event;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

import java.util.Objects;

/**
 * Association 端点：具体 Variant，不固定版次。
 */
public record AssociationEndpointEvent(WorkingSetId workingSetId, LocalId localId, CommonId commonId,
                                       TrackerId trackerId) {

    public AssociationEndpointEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
