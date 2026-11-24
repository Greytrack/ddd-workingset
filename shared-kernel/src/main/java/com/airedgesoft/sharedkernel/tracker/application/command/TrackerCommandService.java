package com.airedgesoft.sharedkernel.tracker.application.command;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.productspace.domain.repository.ProductSpaceRepository;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerCreatedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerDeletedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracker 命令服务：创建业务原生 Tracker、重命名与删除；删除前置校验在此完成。
 */
@Service
@RequiredArgsConstructor
public class TrackerCommandService {
    private final LightTrackerItemQueryProvider lightTrackerItemQueryProvider;
    private final WorkingSetRepository workingSetRepository;
    private final TrackerRepository trackerRepository;
    private final ProductSpaceRepository productSpaceRepository;
    private final ClassificationQueryProvider classifications;
    private final TrackerQueryProvider trackerQueries;
        private final ApplicationEventPublisher events;

    /**
     * 在默认工作集创建业务原生 Tracker。
     */
    @Transactional
    public Tracker create(Long id, String spaceId, String name, Long classificationId,
                          boolean shared) {
        return create(id, spaceId, name, classifications.requireClassification(classificationId), shared);
    }

    /**
     * 在默认工作集创建业务原生 Tracker；共享模式取分类的共享策略（{@code POST /v1/trackers} 契约不带 shared）。
     */
    @Transactional
    public Tracker create(Long id, String spaceId, String name, Long classificationId) {
        ClassSnapshot classification = classifications.requireClassification(classificationId);
        return create(id, spaceId, name, classification, classification.isShared());
    }

    private Tracker create(Long id, String spaceId, String name, ClassSnapshot classification,
                           boolean shared) {
        if (!WorkingSet.DEFAULT_ID.equals(id)) {
            throw new DomainException(BindingResCode.WORKING_SET_NOT_WRITABLE, "只允许在默认工作集新建跟踪分类");
        }
        WorkingSet workingSet = workingSetRepository.findById(id)
                .orElseThrow(() -> new DomainException(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
        ProductSpace space = productSpaceRepository.findById(spaceId)
                .orElseThrow(() -> new DomainException(BindingResCode.PRODUCT_SPACE_NOT_FOUND, spaceId));
        Tracker tracker = Tracker.original(Ids.nextId(), name, space,
                classification, shared, AuthorizationScope.currentUserId());
        trackerRepository.save(tracker);
        events.publishEvent(new TrackerCreatedEvent(
                tracker.trackerId(), workingSet.id(), space.id(), String.valueOf(classification.getId()), shared));
        return tracker;
    }

    /**
     * 重命名 Tracker；名称去首尾空白后 1–255 字符。
     */
    @Transactional
    public Tracker rename(Long id, String name) {
        Tracker tracker = trackerRepository.lockTracker(id)
                .orElseThrow(() -> new DomainException(BindingResCode.TRACKER_NOT_FOUND, Long.toString(id)));
        requireOwningWorkingSet(tracker.workingSetId());
        if (tracker.rename(name)) {
            tracker.touch(AuthorizationScope.currentUserId());
            trackerRepository.save(tracker);
        }
        return tracker;
    }

    /**
     * 删除 Tracker：软删标记，保留 Working Set 绑定关系以支持将来恢复。
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void delete(Long id) {
        Tracker tracker = trackerRepository.lockTracker(id)
                .orElseThrow(() -> new DomainException(BindingResCode.TRACKER_NOT_FOUND, Long.toString(id)));
        requireOwningWorkingSet(tracker.workingSetId());
        tracker.requireDeletable(
                lightTrackerItemQueryProvider.existsInTracker(id) || trackerQueries.hasRegisteredBusinessItems(id),
                trackerQueries.hasOrgChildren(id),
                trackerQueries.hasDerivedChildren(id)
        );
        TrackerDeletedEvent deletedEvent = tracker.delete(AuthorizationScope.currentUserId());
        trackerRepository.save(tracker);
        events.publishEvent(deletedEvent);
    }

    private void requireOwningWorkingSet(Long id) {
        workingSetRepository.findById(id)
                .orElseThrow(() -> new DomainException(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
    }
}
