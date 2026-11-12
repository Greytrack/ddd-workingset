package sk.workingset.application.port;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/**
 * SK 拥有的 LTI 生命周期协作接口（D4 方案 A：LTI 校验/移除移回 SK，业务 BC 不再操作 SK 仓储）。
 *
 * <p>实现位于 SK 应用层，ALM 等业务 BC 只依赖本接口。
 */
public interface TrackerItemLifecyclePort {

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
}
