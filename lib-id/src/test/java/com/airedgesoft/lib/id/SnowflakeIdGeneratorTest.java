package com.airedgesoft.lib.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SnowflakeIdGenerator(1L, 1L);
    }

    @Test
    void testNextId() {
        long id = generator.nextId();
        assertTrue(id > 0);
    }

    @Test
    void testNextIdUniqueness() {
        Set<Long> ids = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            assertTrue(ids.add(generator.nextId()));
        }
        assertEquals(count, ids.size());
    }

    @Test
    void testNextIdMonotonicity() {
        // 大幅时钟回拨（> 5ms）会抛 IllegalStateException；本用例只断言正常路径单调递增
        long previousId = generator.nextId();
        for (int i = 0; i < 1000; i++) {
            long currentId;
            try {
                currentId = generator.nextId();
            } catch (IllegalStateException clockSkew) {
                generator = new SnowflakeIdGenerator(1L, 1L);
                previousId = generator.nextId();
                continue;
            }
            assertTrue(currentId > previousId);
            previousId = currentId;
        }
    }

    @Test
    void testConcurrentNextId() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        AtomicLong totalGenerated = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        assertTrue(ids.add(generator.nextId()));
                        totalGenerated.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * idsPerThread, totalGenerated.get());
        assertEquals(threadCount * idsPerThread, ids.size());
    }

    @Test
    void testInvalidDatacenterId() {
        long max = SnowflakeIdGenerator.getMaxDatacenterNum();
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(max + 1, 1L));
    }

    @Test
    void testInvalidMachineId() {
        long max = SnowflakeIdGenerator.getMaxMachineNum();
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1L, -1L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1L, max + 1));
    }

    @Test
    void testDifferentMachinesGenerateDifferentIds() {
        SnowflakeIdGenerator g1 = new SnowflakeIdGenerator(1L, 1L);
        SnowflakeIdGenerator g2 = new SnowflakeIdGenerator(1L, 2L);
        SnowflakeIdGenerator g3 = new SnowflakeIdGenerator(2L, 1L);

        long id1 = g1.nextId();
        long id2 = g2.nextId();
        long id3 = g3.nextId();

        assertNotEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id3);
    }

    @Test
    void testNextId_smallClockBackward_waitsAndStaysMonotonic() {
        // 1000 取号 → 回拨 5ms（上限内）→ 等待期两次仍落后 → 追平 1000：不抛异常，同毫秒序列递增
        SnowflakeIdGenerator clocked = new SnowflakeIdGenerator(1L, 1L, scriptedClock(1000L, 995L, 996L, 999L, 1000L));

        long first = clocked.nextId();
        long second = clocked.nextId();

        assertTrue(second > first);
    }

    @Test
    void testNextId_largeClockBackward_refuses() {
        // 回拨 6ms（超上限）→ 视为时钟故障拒绝取号，防重复 ID
        SnowflakeIdGenerator clocked = new SnowflakeIdGenerator(1L, 1L, scriptedClock(1000L, 994L));

        clocked.nextId();

        assertThrows(IllegalStateException.class, clocked::nextId);
    }

    /** 按脚本依次返回时间戳，耗尽后停在最后一个值。 */
    private static LongSupplier scriptedClock(Long... stamps) {
        Deque<Long> script = new ArrayDeque<>(Arrays.asList(stamps));
        return () -> script.size() > 1 ? script.poll() : script.peek();
    }
}
