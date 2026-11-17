package sk.workingset.application.port;

import java.util.Map;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/** Merge 写入命令：条目/轮次身份、目标操作、期望锁版本与字段决定。 */
public record RequirementMergeCommand(String roundId, String entryId, String token, Operation operation,
                                      TrackerId trackerId, CommonId commonId, LocalId localId,
                                      Revision revision, Long expectedLockVersion, String version,
                                      Map<String, Object> baseFields, Map<String, Object> acceptedFields,
                                      boolean mergeBackCreate) {
    public enum Operation { CREATE, UPDATE, DELETE, RESTORE }

    /** 目标 BC 实际提交的变体身份及其版次。 */
    public record Result(LocalId localId, Revision revision, boolean created) {}
}
