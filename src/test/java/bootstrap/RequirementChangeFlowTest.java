package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import alm.requirement.infrastructure.InMemoryRequirementRepository;
import alm.requirement.infrastructure.InMemorySnapshotStore;
import alm.requirement.infrastructure.RequirementSnapshot;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;
import sk.workingset.infrastructure.SnowflakeIdGenerator;

/**
 * S2 集成测试：来源普通修改 → 修改前快照 → 来源事件（AT-03 前半）。
 *
 * <p>术语：{@code revision} 手动版次、{@code version} 发布大版本（可空）、
 * {@code lock_version} 乐观锁（数据变动后自动 +1）。
 */
class RequirementChangeFlowTest {

    private static final LocalId L0 = LocalId.of("L0");
    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T0 = TrackerId.of("T0");
    private static final Revision R3 = Revision.of(3);

    private InMemoryRequirementRepository requirements;
    private InMemorySnapshotStore snapshots;
    private List<RequirementChanged> events;
    private RequirementApplicationService service;

    @BeforeEach
    void setUp() {
        requirements = new InMemoryRequirementRepository();
        SnowflakeIdGenerator ids = new SnowflakeIdGenerator(1, 1);
        snapshots = new InMemorySnapshotStore(ids);
        events = new ArrayList<>();
        service = new RequirementApplicationService(
                requirements, snapshots, ids, event -> events.add((RequirementChanged) event));
        service.create(L0, C1, T0, "A", "source description", R3);
    }

    @Test
    void AT03_change_title_keeps_revision_and_version_but_increments_lock_version() {
        Requirement changed = service.changeTitle(L0, "B", LockVersion.of(0));

        assertEquals("B", changed.title());
        assertEquals(R3, changed.revision(), "普通修改不得升级业务 Revision");
        assertEquals(Version.none(), changed.version(), "普通修改不得产生发布大版本");
        assertEquals(LockVersion.of(1), changed.lockVersion(), "数据变动后乐观锁自动 +1");

        assertEquals(1, events.size(), "保存成功后应发布一条来源事件");
        RequirementChanged event = events.get(0);
        assertTrue(String.valueOf(event.eventId()).matches("\\d+"), "eventId 应为雪花 ID");
        assertEquals(L0, event.requirementId());
        assertEquals(L0, event.sourceLocalId());
        assertEquals(R3, event.revision());
        assertNotNull(event.snapshotRef());
        assertNotNull(event.occurredAt());

        RequirementSnapshot snapshot = snapshots.find(event.snapshotRef()).orElseThrow();
        assertEquals("A", snapshot.title(), "快照必须是修改前内容");
        assertEquals(Version.none(), snapshot.version());
        assertEquals(LockVersion.of(0), snapshot.lockVersion());
        assertEquals(R3, snapshot.revision());
        assertEquals("B", requirements.findById(L0).orElseThrow().title(), "业务修改已保存");
        assertEquals(LockVersion.of(1), requirements.findById(L0).orElseThrow().lockVersion());
    }

    @Test
    void event_is_published_only_after_saved_state_is_visible() {
        List<String> observedTitles = new ArrayList<>();
        RequirementApplicationService observing = new RequirementApplicationService(
                requirements,
                snapshots,
                new SnowflakeIdGenerator(1, 1),
                event -> observedTitles.add(requirements.findById(L0).orElseThrow().title()));

        observing.changeTitle(L0, "B", LockVersion.of(0));

        assertEquals(List.of("B"), observedTitles, "事件发布时必须已能读到保存后的业务内容");
    }

    @Test
    void stale_expected_lock_version_is_rejected_without_event_or_snapshot() {
        LockVersionConflictException conflict = assertThrows(LockVersionConflictException.class,
                () -> service.changeTitle(L0, "B", LockVersion.of(99)));

        assertEquals(LockVersion.of(99), conflict.expected());
        assertEquals(LockVersion.of(0), conflict.actual());
        assertEquals("A", requirements.findById(L0).orElseThrow().title(), "冲突时不得覆盖业务内容");
        assertEquals(LockVersion.of(0), requirements.findById(L0).orElseThrow().lockVersion());
        assertTrue(events.isEmpty(), "冲突时不得发布事件");
        assertEquals(0, snapshots.size(), "冲突时不应留下无主快照");
    }

    @Test
    void consecutive_changes_publish_ordered_events_and_capture_each_pre_change_snapshot() {
        service.changeTitle(L0, "B", LockVersion.of(0));
        service.changeTitle(L0, "C", LockVersion.of(1));

        assertEquals(2, events.size());
        assertEquals("A", snapshots.find(events.get(0).snapshotRef()).orElseThrow().title());
        assertEquals("B", snapshots.find(events.get(1).snapshotRef()).orElseThrow().title());
        assertEquals(LockVersion.of(0), snapshots.find(events.get(0).snapshotRef()).orElseThrow().lockVersion());
        assertEquals(LockVersion.of(1), snapshots.find(events.get(1).snapshotRef()).orElseThrow().lockVersion());
        assertEquals(R3, events.get(0).revision());
        assertEquals(R3, events.get(1).revision());
        assertTrue(events.get(0).eventId() < events.get(1).eventId(), "事件身份应保持生成顺序");
        assertEquals("C", requirements.findById(L0).orElseThrow().title());
        assertEquals(LockVersion.of(2), requirements.findById(L0).orElseThrow().lockVersion());
    }

    @Test
    void save_failure_does_not_publish_event_or_change_stored_content() {
        RequirementRepository failing = new RequirementRepository() {
            @Override
            public void save(Requirement requirement) {
                throw new IllegalStateException("save failed");
            }

            @Override
            public Optional<Requirement> findById(LocalId localId) {
                return requirements.findById(localId);
            }
        };
        List<RequirementChanged> published = new ArrayList<>();
        RequirementApplicationService failingService = new RequirementApplicationService(
                failing, snapshots, new SnowflakeIdGenerator(1, 1),
                event -> published.add((RequirementChanged) event));

        assertThrows(IllegalStateException.class,
                () -> failingService.changeTitle(L0, "B", LockVersion.of(0)));

        assertTrue(published.isEmpty(), "保存失败不得发布事件");
        assertEquals("A", requirements.findById(L0).orElseThrow().title());
    }
}
