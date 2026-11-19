package sk.workingset.domain.event;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

import java.util.Map;
import java.util.Objects;

/**
 * SK 已批准应用某个 Merge 条目；ALM 在自己的事务中执行并返回结果事件。
 *
 * <p>事件携带冻结证据和期望版本，ALM 必须重新校验后整体应用。
 */
public record MergeEntryApprovedEvent(String roundId,
                                      String entryId,
                                      String token,
                                      Operation operation,
                                      TrackerId trackerId,
                                      CommonId commonId,
                                      LocalId localId,
                                      Revision sourceRevision,
                                      Revision targetRevision,
                                      Long expectedLockVersion,
                                      String version,
                                      Map<String, Object> baseFields,
                                      Map<String, Object> acceptedFields,
                                      boolean inherited,
                                      boolean mergeBackCreate) {

    public MergeEntryApprovedEvent {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(targetRevision, "targetRevision");
        baseFields = baseFields == null ? Map.of() : Map.copyOf(baseFields);
        acceptedFields = acceptedFields == null ? Map.of() : Map.copyOf(acceptedFields);
    }

    public enum Operation {CREATE, UPDATE, DELETE, RESTORE}
}
