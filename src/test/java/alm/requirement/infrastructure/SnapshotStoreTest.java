package alm.requirement.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import alm.requirement.domain.Requirement;
import sk.workingset.application.event.SnapshotRef;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;
import sk.workingset.infrastructure.SnowflakeIdGenerator;

class SnapshotStoreTest {

    private static final LocalId L0 = LocalId.of("L0");
    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T0 = TrackerId.of("T0");
    private static final Revision R3 = Revision.of(3);

    private final InMemorySnapshotStore store = new InMemorySnapshotStore(new SnowflakeIdGenerator(1, 1));

    @Test
    void captured_snapshot_keeps_pre_change_content_and_full_fields() {
        Requirement before = Requirement.create(L0, C1, T0, "A", "source description", R3);

        SnapshotRef ref = store.save(RequirementSnapshot.of(before));
        RequirementSnapshot snapshot = store.find(ref).orElseThrow();

        assertTrue(ref.id().matches("\\d+"), "快照引用应为雪花 ID: " + ref.id());
        assertEquals(L0, snapshot.localId());
        assertEquals(C1, snapshot.commonId());
        assertEquals(T0, snapshot.trackerId());
        assertEquals("A", snapshot.title());
        assertEquals("source description", snapshot.description());
        assertEquals(R3, snapshot.revision());
        assertEquals(Version.none(), snapshot.version(), "修改前尚未发布大版本");
        assertEquals(LockVersion.of(0), snapshot.lockVersion());
        assertEquals(1, store.size());
    }

    @Test
    void captured_snapshot_keeps_published_version_when_present() {
        Requirement before = Requirement.create(L0, C1, T0, "A", "source description", R3, Version.of("1.2"));

        RequirementSnapshot snapshot = store.find(store.save(RequirementSnapshot.of(before))).orElseThrow();

        assertEquals(Version.of("1.2"), snapshot.version());
    }

    @Test
    void snapshot_is_not_affected_by_later_business_change() {
        Requirement before = Requirement.create(L0, C1, T0, "A", "source description", R3);
        SnapshotRef ref = store.save(RequirementSnapshot.of(before));

        Requirement after = before.changeTitle("B", LockVersion.of(0));

        assertEquals("B", after.title());
        assertEquals(LockVersion.of(1), after.lockVersion());
        assertEquals("A", store.find(ref).orElseThrow().title(), "快照必须保留修改前内容");
        assertEquals(LockVersion.of(0), store.find(ref).orElseThrow().lockVersion());
    }

    @Test
    void unknown_snapshot_ref_returns_empty() {
        assertTrue(store.find(SnapshotRef.of("missing")).isEmpty());
    }
}
