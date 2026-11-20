package sk.tracker.application.eventhandler;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.SharedKernelResultCode;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.domain.Classification;
import sk.workingset.domain.event.TrackerCreatedEvent;
import sk.workingset.domain.event.WorkingSetSpaceBoundEvent;

/** 每个 Working Set 的每个产品空间拥有自己的文件夹 Tracker。 */
@Component
@RequiredArgsConstructor
public class SpaceBoundFolderTrackerListener {
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final IdentifierGenerator ids;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener(id = "bootstrap.SpaceBoundFolderTrackerListener.on(sk.workingset.domain.event.WorkingSetSpaceBoundEvent)")
    public void on(WorkingSetSpaceBoundEvent event) {
        var existing = trackers.findFolderTracker(event.workingSetId(), event.productSpaceId());
        if (existing.isPresent()) {
            events.publishEvent(createdEvent(existing.get()));
            return;
        }
        ProductSpace space = spaces.findById(event.productSpaceId())
                .orElseThrow(() -> DomainException.withMessage(SharedKernelResultCode.EVENT_TARGET_NOT_FOUND,
                        "product space disappeared: " + event.productSpaceId()));
        Tracker folder = Tracker.folderTracker(TrackerId.of(ids.nextId(null).longValue()),
                event.workingSetId(), space);
        trackers.save(folder);
        events.publishEvent(createdEvent(folder));
    }

    private static TrackerCreatedEvent createdEvent(Tracker folder) {
        return new TrackerCreatedEvent(folder.trackerId(), folder.workingSetId(),
                folder.productSpaceId(), Long.toString(Tracker.FOLDER_CLASSIFICATION_ID),
                Classification.SharingMode.UNSHARED);
    }
}
