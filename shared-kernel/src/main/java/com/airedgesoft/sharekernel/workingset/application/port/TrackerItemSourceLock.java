package com.airedgesoft.sharekernel.workingset.application.port;

import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;

import java.util.Optional;

/**
 * 锁定来源业务对象，并读取版次与删除状态。
 *
 * <p>该租约用于与业务 BC 的永久删除路径串行化，降低“来源刚被删除、派生刚被创建”的竞态；
 * 它<strong>不是</strong>跨聚合原子校验，也不能替代目标 BC 在本地事务中重新校验。
 */
public interface TrackerItemSourceLock {

    Optional<State> lock(LocalId sourceLocalId);

    record State(Revision revision, boolean deleted) {
    }
}
