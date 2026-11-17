package sk.workingset.application.eventhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.MergeExecutionService;
import sk.workingset.application.event.MergeRoundSubmitted;
import sk.workingset.domain.merge.MergeFailure;

/** Merge LRO 的异步执行入口：提交决策后由事件驱动逐条推进。 */
@Component
public class MergeRoundSubmittedListener {
    private static final Logger log = LoggerFactory.getLogger(MergeRoundSubmittedListener.class);

    private final MergeExecutionService rounds;

    public MergeRoundSubmittedListener(MergeExecutionService rounds) {
        this.rounds = rounds;
    }

    @ApplicationModuleListener(id = "bootstrap.MergeRoundSubmittedListener.on(sk.workingset.application.event.MergeRoundSubmitted)")
    public void on(MergeRoundSubmitted event) {
        try {
            rounds.apply(event.roundId());
        } catch (MergeFailure failure) {
            // A deleted/cleaned-up round makes the event moot; don't retry it forever.
            if (failure.reason() != MergeFailure.Reason.RoundNotFound) {
                throw failure;
            }
            log.info("Merge round {} no longer exists; submitted event ignored", event.roundId());
        }
    }
}
