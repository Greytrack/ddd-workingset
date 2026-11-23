package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.TrackerItemCommandService;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeAppliedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Merge 业务写入提交后，由 SK 在独立事务中切换/创建目标变体（D15-B：事件交互）。
 *
 * <p>处理幂等；失败保留为 Modulith 未完成事件，不再提供 COW 恢复入口。
 */
@Component
public class MergeAppliedListener {

    private final TrackerItemCommandService lifecycle;

    public MergeAppliedListener(TrackerItemCommandService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementMergeAppliedListener.on(sk.workingset.domain.event.MergeAppliedEvent)")
    public void on(MergeAppliedEvent event) {
        lifecycle.applyMergeTarget(event.target());
    }
}
