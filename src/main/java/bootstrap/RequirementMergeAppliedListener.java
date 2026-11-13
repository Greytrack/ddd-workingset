package bootstrap;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.event.RequirementMergeApplied;
import sk.workingset.application.port.TrackerItemLifecyclePort;

/**
 * Merge 业务写入提交后，由 SK 在独立事务中切换/创建目标变体（D15-B：事件交互）。
 *
 * <p>处理幂等；失败保留为 Modulith 未完成事件，可经 `/api/cow/recover/incomplete` 重投。
 */
@Component
public class RequirementMergeAppliedListener {

    private final TrackerItemLifecyclePort lifecycle;

    public RequirementMergeAppliedListener(TrackerItemLifecyclePort lifecycle) {
        this.lifecycle = lifecycle;
    }

    @ApplicationModuleListener
    public void on(RequirementMergeApplied event) {
        lifecycle.applyMergeTarget(event.target());
    }
}
