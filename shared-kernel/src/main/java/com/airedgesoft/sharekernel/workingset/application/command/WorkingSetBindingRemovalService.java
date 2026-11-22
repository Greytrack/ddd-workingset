package com.airedgesoft.sharekernel.workingset.application.command;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.RequestThreadHolder;
import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单个 Working Set 的 Tracker 解绑；每次调用只修改一个聚合。
 */
@Service
@RequiredArgsConstructor
public class WorkingSetBindingRemovalService {
    private final WorkingSetRepository workingSets;

    /**
     * 解除工作集与 Tracker 的绑定（幂等）；仅修改 WorkingSet 一个聚合。
     */
    @Transactional
    public void remove(WorkingSetId workingSetId, TrackerId trackerId) {
        WorkingSet workingSet = workingSets.findById(workingSetId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, workingSetId.value()));
        if (!workingSet.hasTrackerBinding(trackerId)) {
            return;
        }
        workingSet.unbindTracker(trackerId);
        workingSet.audit(RequestThreadHolder.getUserId());
        workingSets.save(workingSet);
    }
}
