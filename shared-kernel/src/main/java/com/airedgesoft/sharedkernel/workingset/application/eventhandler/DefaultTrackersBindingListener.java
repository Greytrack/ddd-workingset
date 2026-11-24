package com.airedgesoft.sharedkernel.workingset.application.eventhandler;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.productspace.domain.repository.ProductSpaceRepository;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.event.DefaultTrackersProvisionedEvent;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
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
    private final ClassificationQueryProvider classifications;

    @ApplicationModuleListener(id = "bootstrap.DefaultTrackersBindingListener.on(sk.tracker.domain.event.DefaultTrackersProvisionedEvent)")
    public void on(DefaultTrackersProvisionedEvent event) {
        String spaceId = event.productSpaceId().toString();
        ProductSpace space = spaces.findById(spaceId).orElse(null);
        if (space == null) {
            return;
        }
        WorkingSet workingSet = workingSets.findById(WorkingSet.DEFAULT_ID)
                .orElseThrow(() -> new DomainException(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                        "default working set disappeared"));
        if (!workingSet.hasSpaceBinding(spaceId)) {
            workingSet.bindSpace(space, WorkingSet.SpaceOrigin.RELATION);
        }
        for (Long trackerId : event.trackerIds()) {
            if (workingSet.hasTrackerBinding(trackerId)) {
                continue;
            }
            Tracker tracker = trackers.findById(trackerId)
                    .orElseThrow(() -> new DomainException(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                            "tracker disappeared: " + Long.toString(trackerId)));
            workingSet.bindTracker(tracker, classifications.requireClassification(tracker.classificationId()), tracker.shared());
        }
        workingSet.audit(Long.toString(AuthorizationScope.currentUserId()));
        workingSets.save(workingSet);
    }
}
