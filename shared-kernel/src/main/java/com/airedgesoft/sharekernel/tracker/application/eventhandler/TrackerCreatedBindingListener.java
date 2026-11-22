package com.airedgesoft.sharekernel.tracker.application.eventhandler;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.RequestThreadHolder;
import com.airedgesoft.sharekernel.SharedKernelResultCode;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.query.provider.ClassificationCatalog;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerCreatedEvent;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Tracker 创建后，WorkingSet 在自己的事务中将 Tracker 纳入已有空间。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackerCreatedBindingListener {
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ClassificationCatalog classifications;

    @ApplicationModuleListener(id = "bootstrap.TrackerCreatedBindingListener.on(sk.workingset.domain.event.TrackerCreatedEvent)")
    public void on(TrackerCreatedEvent event) {
        WorkingSet workingSet = workingSets.findById(event.workingSetId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                        "working set disappeared: " + Long.toString(event.workingSetId())));
        if (workingSet.hasTrackerBinding(event.trackerId())) {
            return;
        }
        Tracker tracker = trackers.findById(event.trackerId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                        "tracker disappeared: " + Long.toString(event.trackerId())));
        workingSet.bindTracker(tracker, classifications.requireClassification(tracker.classificationId()), event.sharingMode());
        workingSet.audit(RequestThreadHolder.getUserId());
        workingSets.save(workingSet);
    }
}
