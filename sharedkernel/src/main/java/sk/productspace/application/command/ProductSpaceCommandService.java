package sk.productspace.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.event.ProductSpaceCreatedEvent;
import sk.productspace.domain.event.ProductSpaceDeletionRequested;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.command.WorkingSetBindingRemovalService;
import sk.workingset.application.query.provider.BusinessObjectTypeRegistry;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.BindingResCode;

import java.util.List;
import java.util.UUID;

/**
 * 产品空间命令服务：创建与空空间删除。
 */
@Service
@RequiredArgsConstructor
public class ProductSpaceCommandService {
    private final ProductSpaceRepository spaces;
    private final ApplicationEventPublisher events;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final TrackerRepository trackers;
    private final LightTrackerItemQueryProvider itemQueries;
    private final BusinessObjectTypeRegistry registry;
    private final WorkingSetBindingRemovalService bindingRemovals;
    private final IdentifierGenerator ids;

    /**
     * 创建产品空间并发布创建事件。
     */
    @Transactional
    public ProductSpace create(String name) {
        ProductSpace space = new ProductSpace(ids.nextId(null).longValue(), name);
        spaces.save(space);
        events.publishEvent(new ProductSpaceCreatedEvent(space.numericId()));
        return space;
    }

    /**
     * 校验业务引用并可靠登记删除请求。
     */
    @Transactional
    public void delete(String id) {
        ProductSpace space = spaces.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, id));
        requireEmpty(space, trackerQueries.findTrackerIdsInSpace(id));
        events.publishEvent(new ProductSpaceDeletionRequested(id, UUID.randomUUID(), 0));
    }

    /**
     * 每次事件只处理一个工作集、一个 Tracker 或产品空间本身。
     */
    @Transactional
    public void advanceDeletion(ProductSpaceDeletionRequested event) {
        String id = event.productSpaceId();
        ProductSpace space = spaces.findById(id).orElse(null);
        if (space == null) {
            return;
        }
        List<TrackerId> inSpace = trackerQueries.findTrackerIdsInSpace(id);
        requireEmpty(space, inSpace);

        List<Long> bound = queries.findWorkingSetsBoundToSpace(id);
        if (!bound.isEmpty()) {
            bindingRemovals.removeSpaceForDeletion(WorkingSetId.of(bound.getFirst()), id);
        } else if (!inSpace.isEmpty()) {
            deleteLeafTracker(inSpace, id);
        } else {
            spaces.delete(space);
            return;
        }
        events.publishEvent(event.nextStep());
    }

    private void requireEmpty(ProductSpace space, List<TrackerId> inSpace) {
        if (spaces.hasBusinessReferences(space) || inSpace.stream().anyMatch(this::hasItems)) {
            throw DomainException.withMessage(BindingResCode.SPACE_HAS_BUSINESS_REFERENCES, space.id());
        }
        if (trackerQueries.hasExternalTrackerReferences(space.id())) {
            throw DomainException.withMessage(BindingResCode.SPACE_STILL_HAS_TRACKERS, space.id());
        }
    }

    private boolean hasItems(TrackerId id) {
        return itemQueries.existsInTracker(id) || registry.hasBusinessItems(id);
    }

    private void deleteLeafTracker(List<TrackerId> inSpace, String spaceId) {
        TrackerId id = inSpace.stream()
                .filter(tracker -> !trackerQueries.hasOrgChildren(tracker) && !trackerQueries.hasDerivedChildren(tracker))
                .findFirst()
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.SPACE_STILL_HAS_TRACKERS, spaceId));
        Tracker tracker = trackers.lockTracker(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.TRACKER_NOT_FOUND, id.value()));
        tracker.requireDeletable(hasItems(id), trackerQueries.hasOrgChildren(id), trackerQueries.hasDerivedChildren(id));
        trackers.deleteTracker(id);
    }
}
