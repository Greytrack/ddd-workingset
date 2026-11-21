package bootstrap;

import org.junit.jupiter.api.Test;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.BusinessObjectType;
import sk.workingset.application.port.BusinessObjectTypeAdapter;
import sk.workingset.application.port.Capabilities;
import sk.workingset.application.port.TrackerItemSourceLock;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.LightTrackerItemSummary;
import sk.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.infrastructure.providers.DefaultBusinessObjectTypeRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务对象类型路由：tracker_class/classificationId 精确匹配、组织类型与未知类型失败语义。
 */
class BusinessObjectTypeRegistryTest {

    private static final TrackerId TRACKER = TrackerId.of(1L);

    private static DefaultBusinessObjectTypeRegistry registry(BusinessObjectTypeAdapter adapter, Tracker tracker,
                                                              LightTrackerItemQueryProvider items) {
        TrackerRepository trackers = mock(TrackerRepository.class);
        when(trackers.findById(TRACKER)).thenReturn(Optional.of(tracker));
        return new DefaultBusinessObjectTypeRegistry(List.of(adapter), trackers, items);
    }

    private static Tracker tracker(long classificationId, long baseClassId, long classId) {
        return Tracker.reconstitute(TRACKER, TRACKER, "tracker", null, WorkingSetId.of("1"), 10L,
                classificationId, baseClassId, classId, SharingMode.UNSHARED, null, false);
    }

    private static BusinessObjectTypeAdapter requirementAdapter() {
        BusinessObjectTypeAdapter adapter = mock(BusinessObjectTypeAdapter.class);
        when(adapter.types()).thenReturn(Set.of(new BusinessObjectType(14010101L)));
        when(adapter.capabilities()).thenReturn(new Capabilities(true, true));
        when(adapter.sourceLock()).thenReturn(mock(TrackerItemSourceLock.class));
        TrackerItemCatalogQueryProvider catalog = mock(TrackerItemCatalogQueryProvider.class);
        when(catalog.hasAnyItem(any())).thenReturn(true);
        when(adapter.catalog()).thenReturn(catalog);
        return adapter;
    }

    @Test
    void routesByTrackerClassification() {
        BusinessObjectTypeAdapter adapter = requirementAdapter();
        DefaultBusinessObjectTypeRegistry registry = registry(adapter, tracker(14010101L, 1401L, 140101L),
                mock(LightTrackerItemQueryProvider.class));

        assertSame(adapter, registry.forTracker(TRACKER));
    }

    @Test
    void resolvesItemThroughItsTracker() {
        BusinessObjectTypeAdapter adapter = requirementAdapter();
        LightTrackerItemQueryProvider items = mock(LightTrackerItemQueryProvider.class);
        DefaultBusinessObjectTypeRegistry registry = registry(adapter, tracker(14010101L, 1401L, 140101L), items);
        LocalId id = LocalId.of("99");
        when(items.findById(id)).thenReturn(Optional.of(new LightTrackerItemSummary(
                id, CommonId.of("99"), TRACKER, WorkingSetId.of("1"), Revision.of(1), id, Revision.of(1), true)));

        assertSame(adapter, registry.forItem(id));
    }

    @Test
    void organizationalTrackerHasNoBusinessItems() {
        BusinessObjectTypeAdapter adapter = requirementAdapter();
        DefaultBusinessObjectTypeRegistry registry = registry(adapter, tracker(10070101L, 1007L, 100701L),
                mock(LightTrackerItemQueryProvider.class));

        DomainException failure = assertThrows(DomainException.class, () -> registry.forTracker(TRACKER));
        assertEquals(FailureReason.CAPABILITY_NOT_SUPPORTED, FailureReason.of(failure));
    }

    @Test
    void duplicateTypeFailsAtStartup() {
        BusinessObjectTypeAdapter first = requirementAdapter();
        BusinessObjectTypeAdapter second = requirementAdapter();

        assertThrows(IllegalStateException.class,
                () -> new DefaultBusinessObjectTypeRegistry(List.of(first, second), mock(TrackerRepository.class),
                        mock(LightTrackerItemQueryProvider.class)));
    }
}
