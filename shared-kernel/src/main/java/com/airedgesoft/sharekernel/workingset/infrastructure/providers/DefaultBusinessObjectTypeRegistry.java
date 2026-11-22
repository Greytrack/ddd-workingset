package com.airedgesoft.sharekernel.workingset.infrastructure.providers;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.application.port.BusinessObjectType;
import com.airedgesoft.sharekernel.workingset.application.port.BusinessObjectTypeAdapter;
import com.airedgesoft.sharekernel.workingset.application.query.provider.BusinessObjectTypeRegistry;
import com.airedgesoft.sharekernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import org.springframework.stereotype.Component;

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

    private Tracker tracker(TrackerId trackerId) {
        return trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, trackerId.value()));
    }
}
