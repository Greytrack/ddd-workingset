package alm.requirement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import sk.workingset.domain.CommonId;
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
        return Requirement.create(L0, C1, T0, "A", "source description", R3, Version.of(0));
    }

    @Test
    void change_title_returns_new_instance_and_keeps_business_revision() {
        Requirement original = givenRequirement();

        Requirement changed = original.changeTitle("B", Version.of(0));

        assertNotSame(original, changed, "不可变聚合应返回新实例");
        assertEquals("B", changed.title());
        assertEquals("source description", changed.description());
        assertEquals(Version.of(1), changed.version(), "普通修改推进并发版本");
        assertEquals(R3, changed.revision(), "普通修改不得升级业务 Revision");
        assertEquals(L0, changed.localId());
        assertEquals(C1, changed.commonId());
        assertEquals("A", original.title(), "原实例保持不变");
        assertEquals(Version.of(0), original.version());
    }

    @Test
    void change_title_rejects_stale_expected_version() {
        Requirement original = givenRequirement();

        VersionConflictException conflict = assertThrows(VersionConflictException.class,
                () -> original.changeTitle("B", Version.of(1)));

        assertEquals(Version.of(1), conflict.expected());
        assertEquals(Version.of(0), conflict.actual());
        assertEquals("A", original.title(), "冲突时不得修改原实例");
    }

    @Test
    void created_requirement_starts_at_version_zero() {
        assertEquals(Version.of(0), givenRequirement().version());
    }

    @Test
    void change_title_requires_both_arguments() {
        Requirement original = givenRequirement();

        assertThrows(NullPointerException.class, () -> original.changeTitle(null, Version.of(0)));
        assertThrows(NullPointerException.class, () -> original.changeTitle("B", null));
        assertSame(original, original);
    }
}
