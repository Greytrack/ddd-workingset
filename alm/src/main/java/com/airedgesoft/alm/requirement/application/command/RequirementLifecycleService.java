package com.airedgesoft.alm.requirement.application.command;

import com.airedgesoft.alm.requirement.application.port.RequirementMutationStore;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementResultCode;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.LifecycleCommand;
import com.airedgesoft.sharekernel.workingset.domain.LifecycleResult;
import com.airedgesoft.sharekernel.workingset.domain.event.CompleteDeleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;


/**
 * Requirement 生命周期服务：永久删除 guard 与编排（先软删、无来源依赖后方可清除，D14/D7）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementLifecycleService {
    private final RequirementMutationStore requirements;
    private final LightTrackerItemQueryProvider items;
    private final ApplicationEventPublisher eventPublisher;
    private final RequirementCommandService commands;

    private static DomainException missing(Long id) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, Long.toString(id));
    }

    /**
     * 永久删除：先通过 guard（须已软删），无来源依赖时清除对象并发布彻底删除事件。
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void delete2(Long id) {
        Requirement current = requirements.lockById(id).orElseThrow(() -> missing(id));
        commands.requireTrackerWorkingSet(current.trackerId());
        LifecycleResult decision = current.delete2(LifecycleCommand.locked("permanent delete"));
        if (!decision.succeeded()) {
            throw DomainException.withMessage(RequirementResultCode.SOFT_DELETE_REQUIRED, Long.toString(id));
        }
        // 扩展侧读取来源并写入依赖，永久删除侧检查依赖并删除来源。可串行化事务检测交错写入。
        if (items.hasSourceBackedDependents(id)) {
            throw DomainException.withMessage(FailureReason.PREPARING, "Derived items still depend on source " + Long.toString(id));
        }
        requirements.delete(current);
        eventPublisher.publishEvent(new CompleteDeleteEvent(id, current.trackerId(), System.currentTimeMillis()));
    }
}
