package com.airedgesoft.alm.requirement.adapters.workingset;

import com.airedgesoft.alm.requirement.application.port.RequirementMutationStore;
import com.airedgesoft.sharekernel.workingset.application.port.TrackerItemSourceLock;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Requirement 来源行锁租约：与 ALM 的永久删除路径使用同一行锁。
 *
 * <p>该锁只降低竞态，不作为跨聚合原子校验；删除守卫仍在 ALM 本地事务中重新检查依赖。
 */
@Component
@RequiredArgsConstructor
public class RequirementSourceLockAdapter implements TrackerItemSourceLock {

    private final RequirementMutationStore requirements;

    @Override
    public Optional<State> lock(LocalId sourceLocalId) {
        return requirements.lockSourceState(sourceLocalId);
    }
}
