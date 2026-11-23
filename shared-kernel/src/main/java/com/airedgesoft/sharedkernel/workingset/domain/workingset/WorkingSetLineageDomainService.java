package com.airedgesoft.sharedkernel.workingset.domain.workingset;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet.TrackerBinding;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import org.jmolecules.ddd.annotation.Service;

import java.util.List;

/**
 * Compare/Merge 的谱系准入。业务能力与比较证据由 T1 执行入口另行校验。
 */
@Service
public final class WorkingSetLineageDomainService {

    private WorkingSetLineageDomainService() {
    }

    /**
     * 返回直属子级；source/target 必须是相邻父子，否则拒绝。
     */
    public static WorkingSet childOf(WorkingSet source, WorkingSet target) {
        if (source.id().equals(target.parentWorkingSetId())) {
            return target;
        }
        if (target.id().equals(source.parentWorkingSetId())) {
            return source;
        }
        throw DomainException.withMessage(BindingResCode.WORKING_SETS_NOT_ADJACENT, Long.toString(source.id()) + "/" + Long.toString(target.id()));
    }

    /**
     * 双向使用同一子级候选范围。空列表表示无可比较的 Tracker 候选。
     */
    public static List<TrackerBinding> comparableBindings(WorkingSet source, WorkingSet target) {
        return childOf(source, target).trackers().stream()
                .filter(binding -> binding.sharingMode() == SharingMode.UNSHARED)
                .toList();
    }

    /**
     * 只允许“直接派生父子”：child.sourceId 必须等于 sourceTracker.trackerId。
     * 该判断不遍历祖先，也不把同父的其他子 Tracker 视为可配对兄弟。
     */
    public static boolean isDirectDerivation(Tracker child, Tracker sourceTracker) {
        return child.sourceId() != null && child.sourceId().equals(sourceTracker.trackerId());
    }
}
