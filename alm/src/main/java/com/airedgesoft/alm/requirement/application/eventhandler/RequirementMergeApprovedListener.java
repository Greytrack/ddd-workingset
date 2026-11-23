package com.airedgesoft.alm.requirement.application.eventhandler;


import com.airedgesoft.alm.requirement.application.RequirementTypes;
import com.airedgesoft.alm.requirement.application.command.RequirementMergeWriter;
import com.airedgesoft.alm.requirement.application.port.RequirementMergeResultPublications;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryApprovedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryResolvedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * ALM 在自己的事务中应用 SK 批准的 Merge 条目，并发布结果事件。
 *
 * <p>结果发布记录与业务写入同事务提交，并作为批准事件重投的去重证据。
 */
@Component
public class RequirementMergeApprovedListener {
    private final RequirementMergeWriter writer;
    private final ApplicationEventPublisher events;
    private final RequirementMergeResultPublications results;

    public RequirementMergeApprovedListener(RequirementMergeWriter writer, ApplicationEventPublisher events,
                                            RequirementMergeResultPublications results) {
        this.writer = writer;
        this.events = events;
        this.results = results;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementMergeApprovedListener.on(sk.workingset.domain.event.MergeEntryApprovedEvent)")
    public void on(MergeEntryApprovedEvent event) {
        if (!RequirementTypes.SUPPORTED.contains(event.targetClassificationId())) {
            return;
        }
        results.lockEntry(event.roundId(), event.entryId());
        if (results.exists(event.roundId(), event.entryId())) {
            return;
        }
        MergeEntryResolvedEvent result = writer.apply(event);
        events.publishEvent(result);
    }
}
