package sk.workingset.application.command;

import sk.workingset.error.BindingResCode;

import sk.common.error.DomainException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;

/** 单个 Working Set 的 Tracker 解绑；每次调用只修改一个聚合。 */
@Service
@RequiredArgsConstructor
public class WorkingSetBindingRemovalService {
    private final WorkingSetRepository workingSets;

    @Transactional
    public void remove(WorkingSetId workingSetId, TrackerId trackerId) {
        WorkingSet workingSet = workingSets.findById(workingSetId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, workingSetId.value()));
        if (!workingSet.hasTrackerBinding(trackerId)) {
            return;
        }
        workingSet.unbindTracker(trackerId);
        workingSets.save(workingSet);
    }
}
