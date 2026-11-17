package sk.workingset.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import sk.workingset.application.port.IdentifierGenerator;

class SnowflakeIdGeneratorTest {

    @Test
    void generated_ids_are_unique_and_positive() {
        IdentifierGenerator generator = new SnowflakeIdGenerator(1, 2);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            long id = generator.nextId();
            assertTrue(id > 0, "雪花 ID 应为正数");
            assertTrue(ids.add(id), "重复的雪花 ID: " + id);
        }
        assertEquals(10_000, ids.size());
    }

    @Test
    void same_millisecond_ids_are_unique_and_carry_sequence() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        IdentifierGenerator generator = new SnowflakeIdGenerator(1, 2, now::get);

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            ids.add(generator.nextId());
        }

        assertEquals(32, ids.size(), "同一毫秒内 5 位 sequence 可产生 32 个不同 ID");
        assertEquals(0, ids.stream().mapToLong(id -> id & 31L).min().orElse(-1));
        assertEquals(31, ids.stream().mapToLong(id -> id & 31L).max().orElse(-1));
    }

    @Test
    void id_encodes_datacenter_and_machine() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 5, now::get);

        long id = generator.nextId();

        assertEquals(3, (id >> 8) & 7L, "datacenter 位段");
        assertEquals(5, (id >> 5) & 7L, "machine 位段");
        assertEquals(1_700_000_000_000L, (id >> 11) + SnowflakeIdGenerator.START_STAMP, "时间戳位段");
    }

    @Test
    void clock_moving_backwards_is_rejected() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 2, now::get);
        generator.nextId();

        now.addAndGet(-1);

        assertThrows(IllegalStateException.class, generator::nextId);
    }

    @Test
    void invalid_datacenter_or_machine_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(8, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0, 8));
    }
}
