package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.application.command.MergeExecutionService;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeRoundSubmittedEvent;
import com.airedgesoft.sharekernel.workingset.error.MergeResCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Merge LRO 的异步执行入口：提交决策后由事件驱动逐条推进。
 */
@Slf4j
@Component
public class MergeRoundSubmittedListener {

    private final MergeExecutionService rounds;

    public MergeRoundSubmittedListener(MergeExecutionService rounds) {
        this.rounds = rounds;
    }

    @ApplicationModuleListener(id = "bootstrap.MergeRoundSubmittedListener.on(sk.workingset.domain.event.MergeRoundSubmittedEvent)")
    public void on(MergeRoundSubmittedEvent event) {
        try {
            rounds.apply(event.roundId());
        } catch (DomainException failure) {
            if (failure.getResCode() != MergeResCode.ROUND_NOT_FOUND) {
                throw failure;
            }
            log.info("Merge round {} no longer exists; submitted event ignored", event.roundId());
        }
    }
}
