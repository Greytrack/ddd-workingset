package sk.workingset.domain.merge;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.WorkingSetId;

/** 有向的已处理事实。决定版本用于防止晚到确认覆盖更新的事实。 */
public record MergeCheckpoint(Key key, long decisionVersion, Revision sourceRevision, Revision targetRevision,
                              String token, boolean revoked) {
    public record Key(WorkingSetId source, WorkingSetId target, CommonId family) {}
}
