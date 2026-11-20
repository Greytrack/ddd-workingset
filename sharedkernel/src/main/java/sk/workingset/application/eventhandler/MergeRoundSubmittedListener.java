package sk.workingset.application.eventhandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.common.error.DomainException;
import sk.workingset.application.command.MergeExecutionService;
import sk.workingset.domain.event.MergeRoundSubmittedEvent;
import sk.workingset.error.MergeResCode;

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
            // A deleted/cleaned-up round makes the event moot; don't retry it forever.
            if (failure.getResCode() != MergeResCode.ROUND_NOT_FOUND) {
                throw failure;
            }
            log.info("Merge round {} no longer exists; submitted event ignored", event.roundId());
        }
    }
}
