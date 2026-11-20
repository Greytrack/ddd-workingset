package sk.tracker.application.eventhandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.SharedKernelResultCode;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.domain.event.TrackerCreatedEvent;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetRepository;

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
                        "working set disappeared: " + event.workingSetId().value()));
        if (workingSet.hasTrackerBinding(event.trackerId())) {
            return;
        }
        Tracker tracker = trackers.findById(event.trackerId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                        "tracker disappeared: " + event.trackerId().value()));
        workingSet.bindTracker(tracker, classifications.requireClassification(tracker.classificationId()), event.sharingMode());
        workingSets.save(workingSet);
    }
}
