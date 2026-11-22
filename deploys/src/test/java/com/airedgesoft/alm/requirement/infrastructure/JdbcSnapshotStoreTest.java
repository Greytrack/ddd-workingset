package com.airedgesoft.alm.requirement.infrastructure;


import bootstrap.WorkingsetApplication;
import com.airedgesoft.alm.requirement.application.command.RequirementSnapshot;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.infrastructure.repositories.JdbcRequirementSnapshotStore;
import com.airedgesoft.sharekernel.workingset.domain.event.SnapshotRefEvent;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = WorkingsetApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcSnapshotStoreTest {

    private static final Long C1 = Long.valueOf("265506033508288");
    private static final Long T0 = Long.valueOf("264905365135296");
    private static final Integer R3 = 3;
    private final Long localId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    @Autowired
    private JdbcRequirementSnapshotStore store;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IdentifierGenerator ids;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", Long.toString(localId));
    }

    @Test
    void captured_snapshot_keeps_pre_change_content_and_full_fields() {
        Requirement before = Requirement.create(localId, C1, T0, "A", "source description", R3);

        SnapshotRefEvent ref = store.save(RequirementSnapshot.of(before));
        RequirementSnapshot snapshot = store.find(ref).orElseThrow();

        assertTrue(ref.id().matches("\\d+"), "快照引用应为雪花 ID: " + ref.id());
        assertEquals(localId, snapshot.localId());
        assertEquals(C1, snapshot.commonId());
        assertEquals(T0, snapshot.trackerId());
        assertEquals("A", snapshot.title());
        assertEquals("source description", snapshot.description());
        assertEquals(R3, snapshot.revision());
        assertEquals(null, snapshot.version(), "修改前尚未发布大版本");
        assertEquals(Long.valueOf(0), snapshot.lockVersion());
    }

    @Test
    void captured_snapshot_keeps_published_version_when_present() {
        Requirement before = Requirement.create(localId, C1, T0, "A", "source description", R3, "1.2");

        RequirementSnapshot snapshot = store.find(store.save(RequirementSnapshot.of(before))).orElseThrow();

        assertEquals("1.2", snapshot.version());
    }

    @Test
    void snapshot_is_not_affected_by_later_business_change() {
        Requirement before = Requirement.create(localId, C1, T0, "A", "source description", R3);
        SnapshotRefEvent ref = store.save(RequirementSnapshot.of(before));

        Requirement after = before.changeTitle("B", Long.valueOf(0));

        assertEquals("B", after.title());
        assertEquals(Long.valueOf(1), after.lockVersion());
        assertEquals("A", store.find(ref).orElseThrow().title(), "快照必须保留修改前内容");
        assertEquals(Long.valueOf(0), store.find(ref).orElseThrow().lockVersion());
    }

    @Test
    void unknown_snapshot_ref_returns_empty() {
        assertTrue(store.find(SnapshotRefEvent.of(Long.toString(ids.nextId(null).longValue()))).isEmpty());
    }
}
