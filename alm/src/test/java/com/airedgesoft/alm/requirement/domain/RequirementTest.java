package com.airedgesoft.alm.requirement.domain;


import com.airedgesoft.lib.execption.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequirementTest {

    private static final Long L0 = Long.valueOf("260574482192320");
    private static final Long C1 = Long.valueOf("265506033508288");
    private static final Long T0 = Long.valueOf("264905365135296");
    private static final Integer R3 = 3;

    private Requirement givenRequirement() {
        return Requirement.create(L0, C1, T0, "A", "source description", R3);
    }

    @Test
    void change_title_returns_new_instance_with_lock_version_incremented_only() {
        Requirement original = givenRequirement();

        Requirement changed = original.changeTitle("B", Long.valueOf(0));

        assertNotSame(original, changed, "不可变聚合应返回新实例");
        assertEquals("B", changed.title());
        assertEquals("source description", changed.description());
        assertEquals(Long.valueOf(1), changed.lockVersion(), "乐观锁应在数据变动后自动 +1");
        assertEquals(R3, changed.revision(), "普通修改不得升级业务 Revision");
        assertEquals(null, changed.version(), "普通修改不得产生发布大版本");
        assertEquals(L0, changed.localId());
        assertEquals(C1, changed.commonId());
        assertEquals("A", original.title(), "原实例保持不变");
        assertEquals(Long.valueOf(0), original.lockVersion());
    }

    @Test
    void change_title_does_not_override_published_version() {
        Requirement original = Requirement.create(L0, C1, T0, "A", "source description", R3, "2.0");

        Requirement changed = original.changeTitle("B", Long.valueOf(0));

        assertEquals("2.0", changed.version(), "普通修改不得改变已发布大版本");
        assertNotNull(changed.version());
    }

    @Test
    void change_title_rejects_stale_expected_lock_version() {
        Requirement original = givenRequirement();

        LockVersionConflictException conflict = assertThrows(LockVersionConflictException.class,
                () -> original.changeTitle("B", Long.valueOf(1)));

        assertEquals(Long.valueOf(1), conflict.expected());
        assertEquals(Long.valueOf(0), conflict.actual());
        assertEquals("A", original.title(), "冲突时不得修改原实例");
        assertEquals(Long.valueOf(0), original.lockVersion(), "冲突时不得推进乐观锁");
    }

    @Test
    void created_requirement_has_no_version_and_starts_at_lock_version_zero() {
        Requirement requirement = givenRequirement();

        assertEquals(null, requirement.version());
        assertNull(requirement.version(), "未发布大版本时 version 为空");
        assertEquals(Long.valueOf(0), requirement.lockVersion());
    }

    @Test
    void materialized_is_decided_at_construction_and_preserved_by_changes() {
        Requirement created = givenRequirement();
        Requirement inherited = Requirement.inherited(L0, C1, T0, "A", "source description", R3, null);

        assertTrue(created.hasMaterialized());
        assertFalse(inherited.hasMaterialized());
        assertTrue(created.changeTitle("B", Long.valueOf(0)).hasMaterialized());
        Requirement deleted = givenRequirement();
        deleted.delete(Long.valueOf(0));
        assertTrue(deleted.hasMaterialized(), "删除不改变 materialized");
        assertFalse(inherited.changeTitle("B", Long.valueOf(0)).hasMaterialized());
    }

    @Test
    void version_may_be_empty_or_present() {
        assertEquals(null, givenRequirement().version());
        assertEquals("1.0", Requirement.create(L0, C1, T0, "A", "description", R3, "1.0").version());
        assertThrows(DomainException.class,
                () -> Requirement.create(L0, C1, T0, "A", "description", R3, " "));
    }

    @Test
    void change_title_requires_both_arguments() {
        Requirement original = givenRequirement();

        assertThrows(NullPointerException.class, () -> original.changeTitle(null, Long.valueOf(0)));
        assertThrows(NullPointerException.class, () -> original.changeTitle("B", null));
    }
}
