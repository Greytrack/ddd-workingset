package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/** 当前业务 Item 的身份与存在性，由组合根接入相应业务 BC。 */
public interface BusinessTrackerItems {
    Optional<SourceIdentity> findIdentity(LocalId localId);

    boolean existsInTracker(TrackerId trackerId);

    record SourceIdentity(TrackerId trackerId, CommonId commonId) {}
}
