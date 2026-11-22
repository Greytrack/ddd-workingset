package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.RequestThreadHolder;
import com.airedgesoft.sharekernel.SharedKernelResultCode;
import com.airedgesoft.sharekernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharekernel.productspace.domain.repository.ProductSpaceRepository;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.event.DefaultTrackersProvisionedEvent;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.query.provider.ClassificationCatalog;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 默认 Tracker 批量创建完成后，WorkingSet 在一个事务内建立空间与 Tracker 绑定。
 */
@Component
@RequiredArgsConstructor
public class DefaultTrackersBindingListener {
    private final WorkingSetRepository workingSets;
    private final ProductSpaceRepository spaces;
    private final TrackerRepository trackers;
    private final ClassificationCatalog classifications;

    @ApplicationModuleListener(id = "bootstrap.DefaultTrackersBindingListener.on(sk.tracker.domain.event.DefaultTrackersProvisionedEvent)")
    public void on(DefaultTrackersProvisionedEvent event) {
        String spaceId = event.productSpaceId().toString();
        WorkingSet workingSet = workingSets.findById(WorkingSet.DEFAULT_ID)
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                        "default working set disappeared"));
        if (!workingSet.hasSpaceBinding(spaceId)) {
            ProductSpace space = spaces.findById(spaceId)
                    .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                            "product space disappeared: " + spaceId));
            workingSet.bindSpace(space, WorkingSet.SpaceOrigin.RELATION);
        }
        for (Long trackerId : event.trackerIds()) {
            if (workingSet.hasTrackerBinding(trackerId)) {
                continue;
            }
            Tracker tracker = trackers.findById(trackerId)
                    .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                            "tracker disappeared: " + Long.toString(trackerId)));
            workingSet.bindTracker(tracker, classifications.requireClassification(tracker.classificationId()), tracker.sharingMode());
        }
        workingSet.audit(RequestThreadHolder.getUserId());
        workingSets.save(workingSet);
    }
}
