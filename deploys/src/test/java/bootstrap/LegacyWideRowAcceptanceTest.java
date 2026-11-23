package bootstrap;


import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.infrastructure.repositories.JdbcTrackerRepository;
import com.airedgesoft.sharedkernel.tracker.infrastructure.repositories.TrackerCompatibilityRow;
import com.airedgesoft.sharedkernel.tracker.infrastructure.repositories.TrackerRow;
import com.airedgesoft.sharedkernel.workingset.infrastructure.repositories.JdbcWorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.infrastructure.repositories.WorkingSetRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class LegacyWideRowAcceptanceTest {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JdbcWorkingSetRepository workingSets;
    @Autowired
    private JdbcTrackerRepository trackers;

    @Test
    void readsWorkingSetSlotsIndependentlyFromItsOrdinaryColumns() {
        long id = 990001;
        jdbc.update("""
                INSERT INTO air_inst_workingset (id, instance_id, common_id, workingset_id, parent_id,
                    base_class, class, type, number, delete_flag, lock_version, created_by, created_time,
                    updated_by, updated_time, workingset_scop, attributes, text01, text001, endstartdate005)
                VALUES (?, ?, ?, ?, 1, 1008, 100801, 10080101, 'legacy-ws', 0, 0, 'legacy', 1234,
                    'legacy', 1234, 9, '{"enabled": true}'::jsonb, '', 'three-digit', 1700000000123)
                """, id, id, id, id);

        WorkingSetRow row = workingSets.findLegacyRow(id).orElseThrow();
        assertEquals(9L, row.workingSetScope());
        assertEquals("{\"enabled\": true}", row.attributes());
        assertEquals(150, row.customFields().size());
        assertEquals("", row.customFields().get("text01"));
        assertEquals("three-digit", row.customFields().get("text001"));
        assertEquals(1700000000123L, row.customFields().get("endstartdate005"));
        assertTrue(row.customFields().containsKey("numeric010"));
        assertNull(row.customFields().get("numeric010"));
        assertFalse(row.customFields().containsKey("reference1"));
    }

    @Test
    void trackerRenamePreservesLegacyValuesAndCompatibilityReadIsScopedToProductSpace() {
        long spaceId = 990002;
        long id = 990003;
        Long trackerId = id;
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, 'legacy-test')", spaceId);
        jdbc.update("""
                INSERT INTO air_inst_tracker_rev (id, instance_id, common_id, workingset_id, work_space,
                    base_class, class, type, tracker_class, number, name, owner, share, revision, delete_flag,
                    lock_version, created_by, created_time, updated_by, updated_time, version,
                    reference1, reference01, reference001, numeric01, multilist001)
                VALUES (?, ?, ?, 1, ?, 1401, 140101, 14010101, 14010101, 'legacy-tracker', 'before',
                    42, 0, 1, 0, 0, 'legacy', 1234, 'legacy', 1234, 'A.1',
                    101, 102, 103, 1234567890123456.000000000123, '3,2,1')
                """, id, id, id, spaceId);
        jdbc.update("""
                INSERT INTO air_ws_tracker (id, workingset_id, sub_class_id, name, locked, lock_version, attributes)
                VALUES (?, 1, 14010101, 'before', 1, 0, '{"mirror": true}'::jsonb)
                """, id);

        assertTrue(trackers.findLegacyRow(trackerId, spaceId + 1).isEmpty());
        assertTrue(trackers.findLegacyCompatibilityRow(trackerId, spaceId + 1).isEmpty());
        Tracker tracker = trackers.findById(trackerId).orElseThrow();
        assertTrue(tracker.rename("after"));
        tracker.touch(419436436619264L);
        trackers.save(tracker);

        TrackerRow row = trackers.findLegacyRow(trackerId, spaceId).orElseThrow();
        assertEquals("after", row.name());
        assertEquals("A.1", row.version());
        assertNull(row.revisionId());
        assertEquals(153, row.customFields().size());
        assertEquals(101L, row.customFields().get("reference1"));
        assertEquals(102L, row.customFields().get("reference01"));
        assertEquals(103L, row.customFields().get("reference001"));
        assertEquals(new BigDecimal("1234567890123456.000000000123"), row.customFields().get("numeric01"));
        assertEquals("3,2,1", row.customFields().get("multilist001"));

        TrackerCompatibilityRow mirror = trackers.findLegacyCompatibilityRow(trackerId, spaceId).orElseThrow();
        assertEquals("after", mirror.name());
        assertEquals((short) 1, mirror.locked());
        assertNull(mirror.deleteFlag());
        assertEquals("{\"mirror\": true}", mirror.attributes());
    }
}
