package com.airedgesoft.sharedkernel.tracker.application.command;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.sharedkernel.RequestThreadHolder;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.productspace.domain.repository.ProductSpaceRepository;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.event.DefaultTrackersProvisionedEvent;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.Classification;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 产品空间默认 Tracker 批量创建。
 *
 * <p>每个 Tracker 独立提交；全部就绪后发布绑定事件。
 */
@Service
@RequiredArgsConstructor
public class DefaultTrackerInitCommandService {
    private static final Set<Long> INSTANCE_CLASSIFICATIONS = Set.of(10030101L, 10080101L, 10090101L);

    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final ClassificationQueryProvider classifications;
        private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactionManager;

    /**
     * 调用方事务登记绑定事件。重投时复用已在独立事务提交的默认 Tracker。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> provisionAll(Long productSpaceId) {
        String spaceId = productSpaceId.toString();
        TransactionTemplate trackerTransaction = new TransactionTemplate(transactionManager);
        trackerTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        List<Long> provisioned = new ArrayList<>();
        for (Classification classification : classifications.findClassifications()) {
            if (INSTANCE_CLASSIFICATIONS.contains(classification.numericId())) {
                continue;
            }
            Long trackerId = trackerTransaction.execute(status -> provisionDefault(spaceId, classification));
            if (trackerId == null) {
                return List.of();
            }
            provisioned.add(trackerId);
        }
        List<Long> trackerIds = List.copyOf(provisioned);
        events.publishEvent(new DefaultTrackersProvisionedEvent(productSpaceId, trackerIds));
        return trackerIds;
    }

    private Long provisionDefault(String spaceId, Classification classification) {
        // 与空间删除竞争同一行。删除先提交时，迟到的创建事件无需继续初始化。
        ProductSpace space = spaces.findById(spaceId).orElse(null);
        if (space == null) {
            return null;
        }
        return trackers.findDefaultTracker(WorkingSet.DEFAULT_ID, spaceId, classification.numericId())
                .map(Tracker::trackerId)
                .orElseGet(() -> saveDefault(space, classification));
    }

    private Long saveDefault(ProductSpace space, Classification classification) {
        Tracker tracker = Tracker.original(Ids.nextId(),
                space, classification, null, true, RequestThreadHolder.getLongUserId());
        trackers.save(tracker);
        return tracker.trackerId();
    }
}
