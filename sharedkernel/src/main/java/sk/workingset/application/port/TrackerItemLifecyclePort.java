package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * SK 拥有的 LTI 生命周期协作接口（D4 方案 A：LTI 校验/移除移回 SK，业务 BC 不再操作 SK 仓储）。
 *
 * <p>实现位于 SK 应用层，ALM 等业务 BC 只依赖本接口。
 */
public interface TrackerItemLifecyclePort {

    /** 读取未物化变体的身份和内容来源，不锁定也不切换索引。 */
    Optional<InheritedEditTarget> findInheritedTarget(LocalId localId);

    /** 锁定仍在继承内容的目标，供调用方在同一事务内完成用户首次编辑。 */
    Optional<InheritedEditTarget> lockInheritedEditTarget(LocalId localId);

    /** 用户首次编辑保存成功后，在调用方事务内切换到本地内容。 */
    void completeInheritedEdit(LocalId localId, Revision revision);

    record InheritedEditTarget(TargetIdentity identity, WorkingSetId workingSetId,
                               LocalId sourceLocalId, Revision sourceRevision) {}

    /**
     * 在调用方事务内锁定 Tracker 并读取物化目标变体的身份证据。
     *
     * @throws MaterializationRejectedException 目标变体不存在
     */
    TargetIdentity lockMaterializationTarget(TrackerId trackerId, LocalId targetLocalId);

    /**
     * 永久删除业务对象前的前置校验：内容仍被未物化变体依赖时拒绝。
     *
     * <p>在调用方事务内锁定 Tracker，保证判定与业务删除处于同一事务边界。
     */
    void assertPurgeable(TrackerId trackerId, LocalId localId);

    /** 幂等移除业务对象对应的 LTI；由业务对象永久删除事件驱动。 */
    void removePurged(LocalId localId);

    /**
     * 业务原生对象创建后，确保对应 Local-backed LTI 存在。
     *
     * <p>已存在同一 LocalId 的索引时直接返回；用于让新建单个业务对象立即可
     * 被工作集界面读取。调用方保证 {@code workingSetId} 是该 Tracker 的归属工作集。
     */
    void indexLocal(TrackerId trackerId, LocalId localId, CommonId commonId,
                    WorkingSetId workingSetId, Revision revision);

    /**
     * 业务 Merge 写入提交后切换/创建目标变体（D15-B，独立事务、幂等）。
     */
    void applyMergeTarget(MergeTarget target);
}
