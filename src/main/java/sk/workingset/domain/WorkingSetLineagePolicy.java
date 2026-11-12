package sk.workingset.domain;

import java.util.List;

import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSet.TrackerBinding;

/** Compare/Merge 的谱系准入。业务能力与比较证据由 T1 执行入口另行校验。 */
public final class WorkingSetLineagePolicy {

    private WorkingSetLineagePolicy() {}

    /** 双向使用同一子级候选范围。空列表表示无可比较的 Tracker 候选。 */
    public static List<TrackerBinding> comparableBindings(WorkingSet source, WorkingSet target) {
        WorkingSet child;
        if (source.id().equals(target.parentWorkingSetId())) {
            child = target;
        } else if (target.id().equals(source.parentWorkingSetId())) {
            child = source;
        } else {
            throw new BindingFailure(Reason.WorkingSetsNotAdjacent, source.id().value() + "/" + target.id().value());
        }
        return child.trackers().stream().filter(binding -> binding.sharingMode() == SharingMode.UNSHARED).toList();
    }
}
