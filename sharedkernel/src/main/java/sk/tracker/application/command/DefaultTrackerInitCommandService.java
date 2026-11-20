package sk.tracker.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Set;

/**
 * 产品空间默认 Tracker 批量创建。
 *
 * <p>折中实现：一个事务内循环保存默认 Tracker 聚合；绑定关系由 WorkingSet 的独立监听器一次建立。
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

    /**
     * 必须在调用方事务内执行：Tracker 写入与事件 outbox 记录同时提交。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TrackerId> provisionAll(Long productSpaceId) {
        String spaceId = productSpaceId.toString();
        ProductSpace space = spaces.findById(spaceId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, spaceId));
        List<TrackerId> created = new ArrayList<>();
        List<Tracker> pending = new ArrayList<>();
        for (Classification classification : classifications.findClassifications()) {
            if (INSTANCE_CLASSIFICATIONS.contains(classification.numericId())) {
                continue;
            }
            var existing = trackers.findDefaultTracker(WorkingSet.DEFAULT_ID, spaceId, classification.numericId());
            if (existing.isPresent()) {
                created.add(existing.orElseThrow().trackerId());
            } else {
                pending.add(Tracker.original(TrackerId.of(ids.nextId(null).longValue()),
                        space, classification, null, true));
            }
        }
        for (Tracker tracker : pending) {
            trackers.save(tracker);
            created.add(tracker.trackerId());
        }
        events.publishEvent(new DefaultTrackersProvisionedEvent(productSpaceId, created));
        return List.copyOf(created);
    }
}
