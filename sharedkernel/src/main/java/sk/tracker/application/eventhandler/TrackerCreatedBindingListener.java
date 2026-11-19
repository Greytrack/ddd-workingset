package sk.tracker.application.eventhandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.domain.Classification;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.event.TrackerCreatedEvent;
import sk.workingset.domain.repository.WorkingSetRepository;

/** Tracker 创建后，WorkingSet 在自己的事务中完成绑定。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackerCreatedBindingListener {
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ClassificationCatalog classifications;

    @ApplicationModuleListener(id = "bootstrap.TrackerCreatedBindingListener.on(sk.workingset.domain.event.TrackerCreatedEvent)")
    public void on(TrackerCreatedEvent event) {
        WorkingSet workingSet = workingSets.lockWorkingSet(event.workingSetId())
                .orElseThrow(() -> new IllegalStateException(
                        "working set disappeared: " + event.workingSetId().value()));
        if (workingSet.hasTrackerBinding(event.trackerId())) {
            return;
        }
        Tracker tracker = trackers.findTracker(event.trackerId())
                .orElseThrow(() -> new IllegalStateException(
                        "tracker disappeared: " + event.trackerId().value()));
        Classification classification = classifications.findClassification(event.classificationId())
                .orElseThrow(() -> new IllegalStateException(
                        "classification disappeared: " + event.classificationId()));
        workingSet.bindTracker(tracker, classification, event.sharingMode());
        workingSets.save(workingSet);
    }
}
