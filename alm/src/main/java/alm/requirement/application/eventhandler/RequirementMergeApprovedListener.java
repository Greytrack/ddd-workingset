package alm.requirement.application.eventhandler;

import alm.requirement.application.RequirementTypes;
import alm.requirement.application.command.RequirementMergeWriter;
import com.airedgesoft.sharekernel.workingset.application.port.BusinessObjectType;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryApprovedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryResolvedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * ALM 在自己的事务中应用 SK 批准的 Merge 条目，并发布结果事件。
 *
 * <p>幂等凭据与业务写入同事务提交；重复事件返回已保存结果。
 */
@Component
public class RequirementMergeApprovedListener {
    private final RequirementMergeWriter writer;
    private final ApplicationEventPublisher events;

    public RequirementMergeApprovedListener(RequirementMergeWriter writer, ApplicationEventPublisher events) {
        this.writer = writer;
        this.events = events;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementMergeApprovedListener.on(sk.workingset.domain.event.MergeEntryApprovedEvent)")
    public void on(MergeEntryApprovedEvent event) {
        if (!RequirementTypes.SUPPORTED.contains(new BusinessObjectType(event.targetClassificationId()))) {
            return;
        }
        MergeEntryResolvedEvent result = writer.apply(event);
        events.publishEvent(result);
    }
}
