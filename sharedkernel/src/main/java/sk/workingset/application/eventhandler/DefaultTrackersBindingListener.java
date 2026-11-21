package sk.workingset.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.RequestThreadHolder;
import sk.SharedKernelResultCode;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.event.DefaultTrackersProvisionedEvent;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetRepository;

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
        for (TrackerId trackerId : event.trackerIds()) {
            if (workingSet.hasTrackerBinding(trackerId)) {
                continue;
            }
            Tracker tracker = trackers.findById(trackerId)
                    .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                            "tracker disappeared: " + trackerId.value()));
            workingSet.bindTracker(tracker, classifications.requireClassification(tracker.classificationId()), tracker.sharingMode());
        }
        workingSet.audit(RequestThreadHolder.getUserId());
        workingSets.save(workingSet);
    }
}
