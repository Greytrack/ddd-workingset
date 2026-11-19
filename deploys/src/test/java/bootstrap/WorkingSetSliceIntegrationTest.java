package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.application.query.WorkingSetQueryService;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** S1 应用层读取/解析测试：不依赖已退役的单 Item 派生入口。 */
class WorkingSetSliceIntegrationTest {

    private static final WorkingSetId WS0 = WorkingSetId.of("259687804626880");
    private static final WorkingSetId WS1 = WorkingSetId.of("261508476721088");
    private static final LocalId L0 = LocalId.of("260574482192320");
    private static final LocalId L1 = LocalId.of("260574482192321");
    private static final CommonId C1 = CommonId.of("265506033508288");
    private static final TrackerId T0 = TrackerId.of("264905365135296");
    private static final Revision R3 = Revision.of(3);

    private LightTrackerItemRepository items;
    private WorkingSetQueryService workingSet;

    @BeforeEach
    void setUp() {
        items = mock(LightTrackerItemRepository.class);
        WorkingSetQueryProvider query = mock(WorkingSetQueryProvider.class);
        when(query.isPreparing(org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        workingSet = new WorkingSetQueryService(query, items, mock(BusinessTrackerItems.class));
    }

    @Test
    void derived_item_is_source_backed_without_content_copy() {
        LightTrackerItem item = derivedItem();

        assertEquals(C1, item.commonId(), "同一家族保留 CommonId");
        assertEquals(L0, item.sourceLocalId(), "初始来源应指向来源变体");
        assertEquals(R3, item.sourceRevision());
        assertFalse(item.materialized(), "派生初始为 Source-backed");
    }

    @Test
    void resolve_missing_item_is_not_found() {
        DomainException failure = assertThrows(DomainException.class,
                () -> workingSet.resolve(WS1, LocalId.of("266787736178624")));

        assertEquals(FailureReason.NOT_FOUND, FailureReason.of(failure));
    }

    @Test
    void resolve_item_outside_workspace_is_not_included() {
        LightTrackerItem item = derivedItem();
        when(items.findById(item.localId())).thenReturn(Optional.of(item));

        DomainException failure = assertThrows(DomainException.class,
                () -> workingSet.resolve(WS0, item.localId()));

        assertEquals(FailureReason.NOT_INCLUDED, FailureReason.of(failure), "未纳入不得降级为 NotFound");
    }

    @Test
    void local_id_is_stable_and_locates_the_same_variant() {
        LightTrackerItem item = derivedItem();
        LocalId before = item.localId();

        item.switchToLocalBacked(R3);

        assertEquals(before, item.localId(), "物化前后 LocalId 不变");
        when(items.findById(before)).thenReturn(Optional.of(LightTrackerItem.restore(before, item.commonId(),
                item.trackerId(), WS1, R3, before, R3, true)));
        assertEquals(before, workingSet.resolve(WS1, before).localId(), "同一 LocalId 可定位同一变体");
    }

    @Test
    void source_and_target_have_distinct_local_ids() {
        LightTrackerItem item = derivedItem();
        assertNotEquals(L0, item.localId(), "非共享派生必须有独立 LocalId");
        assertTrue(item.localId().value().matches("\\d+"), "LocalId 应为数值 ID: " + item.localId().value());
    }

    private static LightTrackerItem derivedItem() {
        return LightTrackerItem.derive(L1, C1, T0, WS1, R3, L0);
    }
}
