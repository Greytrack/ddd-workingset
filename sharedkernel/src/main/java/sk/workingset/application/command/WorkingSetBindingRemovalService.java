package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.repository.WorkingSetRepository;

/** 单个 Working Set 的 Tracker 解绑；每次调用只修改一个聚合。 */
@Service
@RequiredArgsConstructor
public class WorkingSetBindingRemovalService {
    private final WorkingSetRepository workingSets;

    @Transactional
    public void remove(WorkingSetId workingSetId, TrackerId trackerId) {
        WorkingSet workingSet = workingSets.findByIdForUpdate(workingSetId)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, workingSetId.value()));
        if (!workingSet.hasTrackerBinding(trackerId)) {
            return;
        }
        workingSet.unbindTracker(trackerId);
        workingSets.save(workingSet);
    }
}
