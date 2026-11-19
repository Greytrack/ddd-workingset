package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.workingset.application.command.CowMaterializationCompletionService;
import sk.workingset.domain.event.TrackerItemMaterializationCompletedEvent;

/**
 * 监听 ALM 的实例化完成结果，更新 SK 的 LTI 收据与本地内容状态。
 */
@Component
public class TrackerItemMaterializationCompletedListener {
    private final CowMaterializationCompletionService completion;

    public TrackerItemMaterializationCompletedListener(CowMaterializationCompletionService completion) {
        this.completion = completion;
    }

    @ApplicationModuleListener(id = "bootstrap.TrackerItemMaterializationCompletedListener.on(sk.workingset.domain.event.TrackerItemMaterializationCompletedEvent)")
    public void on(TrackerItemMaterializationCompletedEvent event) {
        completion.complete(event);
    }
}
