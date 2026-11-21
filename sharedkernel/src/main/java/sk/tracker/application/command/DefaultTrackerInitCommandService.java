package sk.tracker.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sk.RequestThreadHolder;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.event.DefaultTrackersProvisionedEvent;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.domain.Classification;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.error.BindingResCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final ClassificationCatalog classifications;
    private final IdentifierGenerator ids;
    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactionManager;

    /**
     * 调用方事务登记绑定事件。重投时复用已在独立事务提交的默认 Tracker。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TrackerId> provisionAll(Long productSpaceId) {
        String spaceId = productSpaceId.toString();
        TransactionTemplate trackerTransaction = new TransactionTemplate(transactionManager);
        trackerTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        List<TrackerId> provisioned = new ArrayList<>();
        for (Classification classification : classifications.findClassifications()) {
            if (INSTANCE_CLASSIFICATIONS.contains(classification.numericId())) {
                continue;
            }
            TrackerId trackerId = trackerTransaction.execute(status -> provisionDefault(spaceId, classification));
            provisioned.add(Objects.requireNonNull(trackerId));
        }
        List<TrackerId> trackerIds = List.copyOf(provisioned);
        events.publishEvent(new DefaultTrackersProvisionedEvent(productSpaceId, trackerIds));
        return trackerIds;
    }

    private TrackerId provisionDefault(String spaceId, Classification classification) {
        // The ProductSpace lookup locks its row. Keep that lock with the Tracker insert and its foreign-key check.
        ProductSpace space = spaces.findById(spaceId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, spaceId));
        return trackers.findDefaultTracker(WorkingSet.DEFAULT_ID, spaceId, classification.numericId())
                .map(Tracker::trackerId)
                .orElseGet(() -> saveDefault(space, classification));
    }

    private TrackerId saveDefault(ProductSpace space, Classification classification) {
        Tracker tracker = Tracker.original(TrackerId.of(ids.nextId(null).longValue()),
                space, classification, null, true, RequestThreadHolder.getLongUserId());
        trackers.save(tracker);
        return tracker.trackerId();
    }
}
