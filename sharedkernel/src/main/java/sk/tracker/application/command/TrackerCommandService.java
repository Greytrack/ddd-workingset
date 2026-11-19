package sk.tracker.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.command.WorkingSetDeletionService;
import sk.workingset.domain.*;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.repository.ProductSpaceRepository;
import sk.workingset.domain.repository.WorkingSetRepository;

@Service
@RequiredArgsConstructor
public class TrackerCommandService {
    private final WorkingSetDeletionService workingSetDeletions;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final IdentifierGenerator ids;

    @Transactional
    public Tracker create(WorkingSetId id, String spaceId, String name, String classificationId,
                          SharingMode sharingMode) {
        if (!WorkingSet.DEFAULT_ID.equals(id)) {
            throw new BindingFailure(Reason.WorkingSetNotWritable, "只允许在默认工作集新建跟踪分类");
        }
        if (Long.parseLong(classificationId) == Tracker.FOLDER_CLASSIFICATION_ID) {
            throw new DomainException(sk.workingset.error.WorkingsetResultCode.BAD_REQUEST, "根文件夹随产品空间创建");
        }
        WorkingSet workingSet = workingSets.lockWorkingSet(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
        ProductSpace space = spaces.lockProductSpace(spaceId)
                .orElseThrow(() -> new BindingFailure(Reason.ProductSpaceNotFound, spaceId));
        workingSet.requireSpaceBinding(spaceId);
        Classification classification = spaces.findClassification(classificationId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "分类不存在：" + classificationId));
        Tracker tracker = Tracker.original(TrackerId.of(ids.nextId(null).longValue()), name, space,
                classification, sharingMode);
        WorkingSet.TrackerBinding binding = workingSet.bindTracker(tracker, classification, sharingMode);
        trackers.insertTracker(tracker);
        workingSets.insertTrackerBinding(binding);
        return tracker;
    }

    @Transactional
    public Tracker rename(TrackerId id, String name) {
        Tracker tracker = trackers.lockTracker(id)
                .filter(locked -> trackers.isVisible(id))
                .orElseThrow(() -> new BindingFailure(Reason.TrackerNotFound, id.value()));
        workingSets.requireReady(tracker.workingSetId());
        if (tracker.rename(name)) {
            trackers.updateName(tracker);
        }
        return tracker;
    }

    public void delete(TrackerId id) {
        workingSetDeletions.deleteTracker(id);
    }
}
