package bootstrap;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.MergeExecutionService;
import sk.workingset.application.event.MergeRoundSubmitted;

/** Merge LRO 的异步执行入口：提交决策后由事件驱动逐条推进。 */
@Component
public class MergeRoundSubmittedListener {
    private final MergeExecutionService rounds;

    public MergeRoundSubmittedListener(MergeExecutionService rounds) {
        this.rounds = rounds;
    }

    @ApplicationModuleListener
    public void on(MergeRoundSubmitted event) {
        rounds.apply(event.roundId());
    }
}
