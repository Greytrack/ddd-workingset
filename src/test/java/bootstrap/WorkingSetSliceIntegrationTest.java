package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.port.ProductSpaceRepository;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.WorkingSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.infrastructure.RequirementAdapter;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.Caller;
import sk.workingset.application.DeriveCommand;
import sk.workingset.application.DeriveResult;
import sk.workingset.application.FailureReason;
import sk.workingset.application.ReadResult;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.infrastructure.SnowflakeIdGenerator;

/**
 * S1 应用层测试，使用真实领域模型和读 Adapter。
 */
class WorkingSetSliceIntegrationTest {

    private static final WorkingSetId WS0 = WorkingSetId.of("WS0");
    private static final WorkingSetId WS1 = WorkingSetId.of("WS1");
    private static final LocalId L0 = LocalId.of("L0");
    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T0 = TrackerId.of("T0");
    private static final Revision R3 = Revision.of(3);
    private static final Caller CALLER = Caller.of("tester");

    private LightTrackerItemRepository items;
    private RequirementRepository requirements;
    private WorkingSetApplicationService workingSet;

    @BeforeEach
    void setUp() {
        items = mock(LightTrackerItemRepository.class);
        requirements = mock(RequirementRepository.class);
        SnowflakeIdGenerator ids = new SnowflakeIdGenerator(1, 1);
        var sets = mock(WorkingSetRepository.class);
        var trackers = mock(TrackerRepository.class);
        var spaces = mock(ProductSpaceRepository.class);
        var space = new ProductSpace("space");
        var classification = new Classification("class", Classification.SharingMode.UNSHARED);
        when(sets.lockWorkingSet(WS1)).thenAnswer(invocation -> {
            var workset = new WorkingSet(WS1, List.of(), List.of());
            workset.bindSpace(space);
            return Optional.of(workset);
        });
        when(trackers.lockTracker(T0)).thenReturn(Optional.of(Tracker.original(T0, space, classification, null)));
        when(spaces.findClassification(classification.id())).thenReturn(Optional.of(classification));
        var businessItems = mock(BusinessTrackerItems.class);
        when(businessItems.findIdentity(L0)).thenReturn(Optional.of(new BusinessTrackerItems.SourceIdentity(T0, C1)));
        workingSet = new WorkingSetApplicationService(
                items, localId -> new RequirementAdapter(requirements, localId), ids, sets, trackers, spaces,
                businessItems);
    }

    @Test
    void AT01_derive_creates_independent_variant_without_copying_content() {
        DeriveResult result = workingSet.derive(new DeriveCommand(WS1, T0, L0, R3, C1));

        LightTrackerItem item = result.item();
        assertEquals(T0, result.tracker().sourceId(), "派生 Tracker 的直接来源应为来源 Tracker");
        assertNotEquals(L0, item.localId(), "非共享派生必须有独立 LocalId");
        assertEquals(C1, item.commonId(), "同一家族保留 CommonId");
        assertFalse(item.materialized(), "派生初始为 Source-backed");
        assertEquals(L0, item.sourceLocalId());
        assertEquals(R3, item.sourceRevision());
        verify(requirements, never()).save(any());
        verify(items).save(item);
    }

    @Test
    void AT02_source_backed_read_resolves_source_content() {
        LightTrackerItem item = workingSet.derive(new DeriveCommand(WS1, T0, L0, R3, C1)).item();

        when(items.findById(item.localId())).thenReturn(Optional.of(item));
        when(requirements.findById(L0)).thenReturn(Optional.of(
                Requirement.create(L0, C1, T0, "A", "source description", R3)));

        ReadResult result = workingSet.read(WS1, item.localId(), CALLER);

        assertEquals("A", result.content().fields().get("title"));
        assertEquals(R3, result.content().revision());
        assertFalse(result.materialized(), "读取不得产生物化副作用");
        assertEquals(L0, result.contentLocalId(), "Source-backed 读取应定位来源变体");
        assertEquals(R3, result.contentRevision());
        verify(requirements).findById(L0);
        verify(requirements, never()).save(any());
    }

    @Test
    void resolve_missing_item_is_not_found() {
        ApplicationFailure failure = assertThrows(ApplicationFailure.class,
                () -> workingSet.resolve(WS1, LocalId.of("missing")));

        assertEquals(FailureReason.NOT_FOUND, failure.reason());
    }

    @Test
    void resolve_item_outside_workspace_is_not_included() {
        LightTrackerItem item = workingSet.derive(new DeriveCommand(WS1, T0, L0, R3, C1)).item();

        when(items.findById(item.localId())).thenReturn(Optional.of(item));

        ApplicationFailure failure = assertThrows(ApplicationFailure.class,
                () -> workingSet.resolve(WS0, item.localId()));

        assertEquals(FailureReason.NOT_INCLUDED, failure.reason(), "未纳入不得降级为 NotFound");
    }

    @Test
    void AC02_local_id_is_stable_and_locates_the_same_variant() {
        LightTrackerItem item = workingSet.derive(new DeriveCommand(WS1, T0, L0, R3, C1)).item();
        LocalId before = item.localId();

        item.switchToLocalBacked(R3);

        assertEquals(before, item.localId(), "物化前后 LocalId 不变");
        when(items.findById(before)).thenReturn(Optional.of(LightTrackerItem.restore(before, item.commonId(),
                item.trackerId(), WS1, R3, before, R3, true)));
        assertEquals(before, workingSet.resolve(WS1, before).localId(), "同一 LocalId 可定位同一变体");
    }

    @Test
    void derived_local_id_is_a_snowflake_value_not_uuid() {
        LightTrackerItem item = workingSet.derive(new DeriveCommand(WS1, T0, L0, R3, C1)).item();

        assertTrue(item.localId().value().matches("\\d+"), "LocalId 应为雪花 ID（纯数字）: " + item.localId().value());
        assertTrue(item.trackerId().value().matches("\\d+"), "TrackerId 应为雪花 ID（纯数字）: " + item.trackerId().value());
    }
}
