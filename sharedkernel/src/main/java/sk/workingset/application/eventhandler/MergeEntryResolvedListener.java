package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.MergeExecutionService;
import sk.workingset.domain.event.MergeEntryResolvedEvent;

/**
 * SK 消费 ALM 条目结果，推进轮次并在需要时发起 Checkpoint 确认。
 */
@Component
public class MergeEntryResolvedListener {
    private final MergeExecutionService rounds;

    public MergeEntryResolvedListener(MergeExecutionService rounds) {
        this.rounds = rounds;
    }

    @ApplicationModuleListener(id = "bootstrap.MergeEntryResolvedListener.on(sk.workingset.domain.event.MergeEntryResolvedEvent)")
    public void on(MergeEntryResolvedEvent event) {
        rounds.recordResult(event);
    }
}
