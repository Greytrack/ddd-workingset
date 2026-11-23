package com.airedgesoft.sharedkernel.productspace.application.command;

import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.productspace.domain.event.ProductSpaceCreatedEvent;
import com.airedgesoft.sharedkernel.productspace.domain.event.ProductSpaceDeleted;
import com.airedgesoft.sharedkernel.productspace.domain.repository.ProductSpaceRepository;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final LightTrackerItemQueryProvider itemQueries;
    
    /**
     * 创建产品空间并发布创建事件。
     */
    @Transactional
    public ProductSpace create(String name) {
        ProductSpace space = new ProductSpace(Ids.nextId(), name);
        spaces.save(space);
        events.publishEvent(new ProductSpaceCreatedEvent(space.numericId()));
        return space;
    }

    /**
     * 删除无有效引用和 Tracker 的产品空间。
     */
    @Transactional
    public void delete(String id) {
        ProductSpace space = spaces.findByIdForDeletion(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, id));
        requireEmpty(space);
        ProductSpaceDeleted deleted = space.delete();
        spaces.save(space);
        events.publishEvent(deleted);
    }

    private void requireEmpty(ProductSpace space) {
        if (queries.hasWorkingSetBinding(space.id())) {
            throw DomainException.withMessage(BindingResCode.SPACE_STILL_BOUND, space.id());
        }
        if (spaces.hasBusinessReferences(space)
                || trackerQueries.findTrackerIdsInSpace(space.id()).stream().anyMatch(itemQueries::existsInTracker)) {
            throw DomainException.withMessage(BindingResCode.SPACE_HAS_BUSINESS_REFERENCES, space.id());
        }
        if (trackerQueries.hasActiveTrackersInSpace(space.id()) || trackerQueries.hasExternalTrackerReferences(space.id())) {
            throw DomainException.withMessage(BindingResCode.SPACE_STILL_HAS_TRACKERS, space.id());
        }
    }

}
