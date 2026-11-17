package sk.workingset.application.event;

import java.util.Objects;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** Association 端点：具体 Variant，不固定版次。 */
public record AssociationEndpoint(WorkingSetId workingSetId, LocalId localId, CommonId commonId,
                                  TrackerId trackerId) {

    public AssociationEndpoint {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
