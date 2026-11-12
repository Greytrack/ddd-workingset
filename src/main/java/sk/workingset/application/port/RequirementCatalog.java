package sk.workingset.application.port;

import java.util.List;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/** 查询 ALM 中该 Tracker 的业务身份，内容仍由 TrackerItemReadPort 提供。 */
@FunctionalInterface
public interface RequirementCatalog {
    List<Identity> identities(TrackerId trackerId);

    record Identity(LocalId localId, CommonId commonId) {}
}
