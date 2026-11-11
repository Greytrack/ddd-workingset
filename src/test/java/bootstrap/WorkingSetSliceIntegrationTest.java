package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.infrastructure.InMemoryRequirementRepository;
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
import sk.workingset.infrastructure.InMemoryLightTrackerItemRepository;

/**
 * S1 纵向切片集成测试（位于组合根包：允许同时引用 SK 与 ALM 实现）。
 */
class WorkingSetSliceIntegrationTest {

    private static final WorkingSetId WS0 = WorkingSetId.of("WS0");
    private static final WorkingSetId WS1 = WorkingSetId.of("WS1");
    private static final LocalId L0 = LocalId.of("L0");
    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T0 = TrackerId.of("T0");
    private static final Revision R3 = Revision.of(3);
    private static final Caller CALLER = Caller.of("tester");

    private InMemoryLightTrackerItemRepository items;
    private InMemoryRequirementRepository requirements;
    private WorkingSetApplicationService workingSet;

    @BeforeEach
    void setUp() {
        items = new InMemoryLightTrackerItemRepository();
        requirements = new InMemoryRequirementRepository();
        RequirementApplicationService requirementApp = new RequirementApplicationService(requirements);
        workingSet = new WorkingSetApplicationService(
                items, localId -> new RequirementAdapter(requirements, localId));
        // 来源 Requirement R0：默认工作集 WS0、revision 3、title=A
        requirementApp.create(L0, C1, T0, "A", "source description", R3);
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
        assertEquals(1, requirements.size(), "派生不复制业务内容");
        assertEquals(1, items.size());
    }

    @Test
    void AT02_source_backed_read_resolves_source_content() {
        LightTrackerItem item = workingSet.derive(new DeriveCommand(WS1, T0, L0, R3, C1)).item();

        ReadResult result = workingSet.read(WS1, item.localId(), CALLER);

        assertEquals("A", result.content().fields().get("title"));
        assertEquals(R3, result.content().revision());
        assertFalse(result.materialized(), "读取不得产生物化副作用");
        assertEquals(L0, result.contentLocalId(), "Source-backed 读取应定位来源变体");
        assertEquals(R3, result.contentRevision());
        assertEquals(1, requirements.size(), "读取不得新建业务对象");
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
        assertSame(item, workingSet.resolve(WS1, before), "同一 LocalId 可定位同一变体");
    }
}
