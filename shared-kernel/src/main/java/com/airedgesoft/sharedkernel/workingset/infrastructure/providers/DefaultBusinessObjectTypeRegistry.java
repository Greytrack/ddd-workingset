package com.airedgesoft.sharedkernel.workingset.infrastructure.providers;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.port.BusinessObjectTypeAdapter;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.BusinessObjectTypeRegistry;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
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
            Set<Long> types = adapter.types();
            if (adapter.catalog() == null) {
                throw new IllegalStateException("Business object type lacks catalog: " + types);
            }
            for (Long type : types) {
                BusinessObjectTypeAdapter previous = byType.putIfAbsent(type, adapter);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate business object type: " + type);
                }
            }
        }
        return Map.copyOf(byType);
    }

    @Override
    public BusinessObjectTypeAdapter forTracker(Long trackerId) {
        Tracker tracker = tracker(trackerId);
        BusinessObjectTypeAdapter adapter = byType.get(tracker.numericClassificationId());
        if (adapter == null) {
            throw new DomainException(FailureReason.CAPABILITY_NOT_SUPPORTED,
                    "No business object adapter for tracker classification "
                            + tracker.numericClassificationId() + " (tracker " + Long.toString(trackerId) + ")");
        }
        return adapter;
    }

    @Override
    public BusinessObjectTypeAdapter forItem(Long localId) {
        return items.findById(localId)
                .map(item -> forTracker(item.trackerId()))
                .orElseThrow(() -> new DomainException(FailureReason.NOT_FOUND,
                        "item index not found: " + localId));
    }

    private Tracker tracker(Long trackerId) {
        return trackers.findById(trackerId)
                .orElseThrow(() -> new DomainException(FailureReason.NOT_FOUND, Long.toString(trackerId)));
    }
}
