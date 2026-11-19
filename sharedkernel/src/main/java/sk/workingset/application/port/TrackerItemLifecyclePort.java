package sk.workingset.application.port;

import sk.workingset.application.common.InheritedEditTarget;
import sk.workingset.application.common.TargetIdentity;

import sk.workingset.domain.event.MergeTargetEvent;

import java.util.Optional;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/**
 * SK 拥有的 LTI 生命周期协作接口（D4 方案 A：LTI 校验/移除移回 SK，业务 BC 不再操作 SK 仓储）。
 *
 * <p>实现位于 SK 应用层，ALM 等业务 BC 只依赖本接口。
 */
public interface TrackerItemLifecyclePort {

    /** 读取未物化变体的身份和内容来源，不锁定也不切换索引。 */
    Optional<InheritedEditTarget> findInheritedTarget(LocalId localId);

}
