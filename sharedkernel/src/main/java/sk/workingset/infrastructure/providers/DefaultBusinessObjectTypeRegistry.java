package sk.workingset.infrastructure.providers;

import org.springframework.stereotype.Component;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.BusinessObjectType;
import sk.workingset.application.port.BusinessObjectTypeAdapter;
import sk.workingset.application.query.provider.BusinessObjectTypeRegistry;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.domain.LocalId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认类型路由：Tracker 的 {@code tracker_class}/{@code classificationId} 就是业务对象类型。
 *
 * <p>启动时校验类型唯一性与摘要能力；SK 自己的组织型 Tracker 明确视为没有业务 Item，
 * 未注册的业务类型则明确失败，避免删除了仍有业务 Item 的 Tracker。
 */
@Component
public class DefaultBusinessObjectTypeRegistry implements BusinessObjectTypeRegistry {

    private static final Set<Long> ORGANIZATIONAL_CLASSIFICATIONS =
            Set.of(10030101L, 10070101L, 10080101L, 10090101L);

    private final TrackerRepository trackers;
    private final LightTrackerItemQueryProvider items;
    private final Map<Long, BusinessObjectTypeAdapter> byType;

    public DefaultBusinessObjectTypeRegistry(List<BusinessObjectTypeAdapter> adapters,
                                             TrackerRepository trackers,
                                             LightTrackerItemQueryProvider items) {
        this.trackers = trackers;
        this.items = items;
        this.byType = index(adapters == null ? List.of() : adapters);
    }

    private static Map<Long, BusinessObjectTypeAdapter> index(List<BusinessObjectTypeAdapter> adapters) {
        Map<Long, BusinessObjectTypeAdapter> byType = new HashMap<>();
        for (BusinessObjectTypeAdapter adapter : adapters) {
            Set<BusinessObjectType> types = adapter.types();
            if (adapter.catalog() == null) {
                throw new IllegalStateException("Business object type lacks catalog: " + types);
            }
            if (adapter.sourceLock() == null) {
                throw new IllegalStateException("Business object type lacks source lock: " + types);
            }
            for (BusinessObjectType type : types) {
                BusinessObjectTypeAdapter previous = byType.putIfAbsent(type.value(), adapter);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate business object type: " + type.value());
                }
            }
        }
        return Map.copyOf(byType);
    }

    @Override
    public BusinessObjectTypeAdapter forTracker(TrackerId trackerId) {
        Tracker tracker = tracker(trackerId);
        BusinessObjectTypeAdapter adapter = byType.get(tracker.numericClassificationId());
        if (adapter == null) {
            throw DomainException.withMessage(FailureReason.CAPABILITY_NOT_SUPPORTED,
                    "No business object adapter for tracker classification "
                            + tracker.numericClassificationId() + " (tracker " + trackerId.value() + ")");
        }
        return adapter;
    }

    @Override
    public BusinessObjectTypeAdapter forItem(LocalId localId) {
        return items.findById(localId)
                .map(item -> forTracker(item.trackerId()))
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "item index not found: " + localId.value()));
    }

    @Override
    public boolean hasBusinessItems(TrackerId trackerId) {
        Tracker tracker = tracker(trackerId);
        if (ORGANIZATIONAL_CLASSIFICATIONS.contains(tracker.numericClassificationId())) {
            return false;
        }
        BusinessObjectTypeAdapter adapter = byType.get(tracker.numericClassificationId());
        if (adapter == null) {
            throw DomainException.withMessage(FailureReason.CAPABILITY_NOT_SUPPORTED,
                    "No business object adapter for tracker classification "
                            + tracker.numericClassificationId() + " (tracker " + trackerId.value() + ")");
        }
        return adapter.catalog().hasAnyItem(trackerId);
    }

    private Tracker tracker(TrackerId trackerId) {
        return trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, trackerId.value()));
    }
}
