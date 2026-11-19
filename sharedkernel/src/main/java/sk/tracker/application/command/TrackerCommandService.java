package sk.tracker.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.*;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.domain.repository.WorkingSetRepository;

@Service
@RequiredArgsConstructor
public class TrackerCommandService {
    private final LightTrackerItemQueryProvider itemQueries;
    private final BusinessTrackerItems businessItems;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final ClassificationCatalog classifications;
    private final TrackerQueryProvider trackerQueries;
    private final IdentifierGenerator ids;
    private final ApplicationEventPublisher events;

    @Transactional
    public Tracker create(WorkingSetId id, String spaceId, String name, String classificationId,
                          SharingMode sharingMode) {
        if (!WorkingSet.DEFAULT_ID.equals(id)) {
            throw new BindingFailure(Reason.WorkingSetNotWritable, "只允许在默认工作集新建跟踪分类");
        }
        if (Long.parseLong(classificationId) == Tracker.FOLDER_CLASSIFICATION_ID) {
            throw new DomainException(sk.workingset.error.WorkingsetResultCode.BAD_REQUEST, "根文件夹随产品空间创建");
        }
        WorkingSet workingSet = workingSets.findById(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
        ProductSpace space = spaces.findById(spaceId)
                .orElseThrow(() -> new BindingFailure(Reason.ProductSpaceNotFound, spaceId));
        workingSet.requireSpaceBinding(spaceId);
        Classification classification = classifications.findClassification(classificationId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "分类不存在：" + classificationId));
        Tracker tracker = Tracker.original(TrackerId.of(ids.nextId(null).longValue()), name, space,
                classification, sharingMode);
        workingSet.bindTracker(tracker, classification, sharingMode);
        trackers.save(tracker);
        events.publishEvent(new sk.workingset.domain.event.TrackerCreatedEvent(
                tracker.trackerId(), workingSet.id(), space.id(), classification.id(), sharingMode));
        return tracker;
    }

    @Transactional
    public Tracker rename(TrackerId id, String name) {
        Tracker tracker = trackers.lockTracker(id)
                .filter(locked -> trackerQueries.isVisible(id))
                .orElseThrow(() -> new BindingFailure(Reason.TrackerNotFound, id.value()));
        workingSets.requireReady(tracker.workingSetId());
        if (tracker.rename(name)) {
            trackers.save(tracker);
        }
        return tracker;
    }

    @Transactional
    public void delete(TrackerId id) {
        Tracker tracker = trackers.lockTracker(id)
                .orElseThrow(() -> new BindingFailure(Reason.TrackerNotFound, id.value()));
        if (tracker.isRootFolder()) {
            throw DomainException.withMessage(sk.workingset.error.WorkingsetResultCode.BAD_REQUEST,
                    "根文件夹须随产品空间卸载");
        }
        workingSets.requireReady(tracker.workingSetId());
        tracker.requireDeletable(itemQueries.existsInTracker(id) || businessItems.existsInTracker(id),
                trackerQueries.hasOrgChildren(id), trackerQueries.hasDerivedChildren(id));
        events.publishEvent(new sk.workingset.domain.event.TrackerDeletionRequestedEvent(id));
    }
}
