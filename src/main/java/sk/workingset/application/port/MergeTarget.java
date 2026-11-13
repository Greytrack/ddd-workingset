package sk.workingset.application.port;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * Merge 业务写入完成后的目标变体事实（D15-B）。
 *
 * @param localId         目标业务对象身份
 * @param commonId        家族身份
 * @param trackerId       目标 Tracker
 * @param workingSetId    目标工作集（`round.target()`）
 * @param revision        业务写入后的版本
 * @param inherited       目标原本是继承变体，需要切换为本地物化
 * @param create          需要在目标工作集新建本地 LightTrackerItem
 */
public record MergeTarget(LocalId localId, CommonId commonId, TrackerId trackerId,
                          WorkingSetId workingSetId, Revision revision,
                          boolean inherited, boolean create) {
}
