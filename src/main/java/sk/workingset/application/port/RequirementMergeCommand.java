package sk.workingset.application.port;

import java.util.Map;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

public record RequirementMergeCommand(String roundId, String entryId, String token, Operation operation,
                                      TrackerId trackerId, CommonId commonId, LocalId localId,
                                      Revision revision, Long expectedLockVersion, String version,
                                      Map<String, Object> baseFields, Map<String, Object> acceptedFields) {
    public enum Operation { CREATE, UPDATE, DELETE, RESTORE }
}
