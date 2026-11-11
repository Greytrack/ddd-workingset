package alm.requirement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

class RequirementTest {

    private static final LocalId L0 = LocalId.of("L0");
    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T0 = TrackerId.of("T0");
    private static final Revision R3 = Revision.of(3);

    private Requirement givenRequirement() {
        return Requirement.create(L0, C1, T0, "A", "source description", R3);
    }

    @Test
    void change_title_returns_new_instance_with_lock_version_incremented_only() {
        Requirement original = givenRequirement();

        Requirement changed = original.changeTitle("B", LockVersion.of(0));

        assertNotSame(original, changed, "不可变聚合应返回新实例");
        assertEquals("B", changed.title());
        assertEquals("source description", changed.description());
        assertEquals(LockVersion.of(1), changed.lockVersion(), "乐观锁应在数据变动后自动 +1");
        assertEquals(R3, changed.revision(), "普通修改不得升级业务 Revision");
        assertEquals(Version.none(), changed.version(), "普通修改不得产生发布大版本");
        assertEquals(L0, changed.localId());
        assertEquals(C1, changed.commonId());
        assertEquals("A", original.title(), "原实例保持不变");
        assertEquals(LockVersion.of(0), original.lockVersion());
    }

    @Test
    void change_title_does_not_override_published_version() {
        Requirement original = Requirement.create(L0, C1, T0, "A", "source description", R3, Version.of("2.0"));

        Requirement changed = original.changeTitle("B", LockVersion.of(0));

        assertEquals(Version.of("2.0"), changed.version(), "普通修改不得改变已发布大版本");
        assertTrue(changed.version().isPresent());
    }

    @Test
    void change_title_rejects_stale_expected_lock_version() {
        Requirement original = givenRequirement();

        LockVersionConflictException conflict = assertThrows(LockVersionConflictException.class,
                () -> original.changeTitle("B", LockVersion.of(1)));

        assertEquals(LockVersion.of(1), conflict.expected());
        assertEquals(LockVersion.of(0), conflict.actual());
        assertEquals("A", original.title(), "冲突时不得修改原实例");
        assertEquals(LockVersion.of(0), original.lockVersion(), "冲突时不得推进乐观锁");
    }

    @Test
    void created_requirement_has_no_version_and_starts_at_lock_version_zero() {
        Requirement requirement = givenRequirement();

        assertEquals(Version.none(), requirement.version());
        assertFalse(requirement.version().isPresent(), "未发布大版本时 version 为空");
        assertEquals(LockVersion.of(0), requirement.lockVersion());
    }

    @Test
    void version_may_be_empty_or_present() {
        assertFalse(Version.none().isPresent());
        assertEquals(null, Version.none().value());
        assertTrue(Version.of("1.0").isPresent());
        assertEquals("1.0", Version.of("1.0").value());
        assertThrows(IllegalArgumentException.class, () -> Version.of(" "));
    }

    @Test
    void change_title_requires_both_arguments() {
        Requirement original = givenRequirement();

        assertThrows(NullPointerException.class, () -> original.changeTitle(null, LockVersion.of(0)));
        assertThrows(NullPointerException.class, () -> original.changeTitle("B", null));
    }
}
