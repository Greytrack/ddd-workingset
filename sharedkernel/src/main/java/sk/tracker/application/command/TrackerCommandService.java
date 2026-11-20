package sk.tracker.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;

/**
 * Tracker 命令服务：创建业务原生 Tracker、重命名与删除；删除前置校验在此完成。
 */
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

    /**
     * 在默认工作集创建业务原生 Tracker；Folder Tracker 不在此创建（随产品空间创建）。
     */
    @Transactional
    public Tracker create(WorkingSetId id, String spaceId, String name, String classificationId,
                          SharingMode sharingMode) {
        if (!WorkingSet.DEFAULT_ID.equals(id)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_NOT_WRITABLE, "只允许在默认工作集新建跟踪分类");
        }
        if (Long.parseLong(classificationId) == Tracker.FOLDER_CLASSIFICATION_ID) {
            throw new DomainException(sk.workingset.error.WorkingsetResultCode.BAD_REQUEST, "文件夹 Tracker 随产品空间创建");
        }
        WorkingSet workingSet = workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
        ProductSpace space = spaces.findById(spaceId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, spaceId));
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

    /**
     * 重命名可见 Tracker；名称去首尾空白后 1–255 字符。
     */
    @Transactional
    public Tracker rename(TrackerId id, String name) {
        Tracker tracker = trackers.lockTracker(id)
                .filter(locked -> trackerQueries.isVisible(id))
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.TRACKER_NOT_FOUND, id.value()));
        requireOwningWorkingSet(tracker.workingSetId());
        if (tracker.rename(name)) {
            trackers.save(tracker);
        }
        return tracker;
    }

    /**
     * 删除 Tracker；须通过无 Item/组织子级/派生子级三项检查，随后发布删除请求事件。
     */
    @Transactional
    public void delete(TrackerId id) {
        Tracker tracker = trackers.lockTracker(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.TRACKER_NOT_FOUND, id.value()));
        if (tracker.isFolderTracker()) {
            throw DomainException.withMessage(sk.workingset.error.WorkingsetResultCode.BAD_REQUEST,
                    "文件夹 Tracker 须随产品空间卸载");
        }
        requireOwningWorkingSet(tracker.workingSetId());
        tracker.requireDeletable(itemQueries.existsInTracker(id) || businessItems.existsInTracker(id),
                trackerQueries.hasOrgChildren(id), trackerQueries.hasDerivedChildren(id));
        events.publishEvent(new sk.workingset.domain.event.TrackerDeletionRequestedEvent(id));
    }

    private void requireOwningWorkingSet(WorkingSetId id) {
        workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
    }
}
