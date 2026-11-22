package com.airedgesoft.sharekernel.workingset.application;

import com.airedgesoft.sharekernel.workingset.application.command.SourceSerialCowCoordinator;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class SourceSerialCowCoordinatorTest {
    @Test
    void queued_tasks_run_by_sequence_while_another_source_can_progress() throws InterruptedException {
        SourceSerialCowCoordinator coordinator = new SourceSerialCowCoordinator();
        LocalId source = LocalId.of("265222440669120");
        CopyOnWriteArrayList<Integer> order = new CopyOnWriteArrayList<Integer>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Thread first = Thread.ofVirtual().start(() -> coordinator.runSerial(source, 1, () -> {
            order.add(1);
            firstStarted.countDown();
            try {
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        }));
        try {
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            Thread third = Thread.ofVirtual().start(() -> coordinator.runSerial(source, 3, () -> order.add(3)));
            await().atMost(Duration.ofSeconds(3)).until(() -> third.getState() == Thread.State.WAITING);
            Thread second = Thread.ofVirtual().start(() -> coordinator.runSerial(source, 2, () -> order.add(2)));
            await().atMost(Duration.ofSeconds(3)).until(() -> second.getState() == Thread.State.WAITING);
            coordinator.runSerial(LocalId.of("265987761440704"), 1, () -> order.add(0));
            assertEquals(List.of(1, 0), order);
            releaseFirst.countDown();
            assertTrue(first.join(Duration.ofSeconds(3)));
            assertTrue(second.join(Duration.ofSeconds(3)));
            assertTrue(third.join(Duration.ofSeconds(3)));
            assertEquals(List.of(1, 0, 2, 3), order);
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void sequence_two_waits_for_one_without_blocking_another_source() throws InterruptedException {
        SourceSerialCowCoordinator coordinator = new SourceSerialCowCoordinator(Duration.ofSeconds(3));
        LocalId source = LocalId.of("262840182583232");
        CopyOnWriteArrayList<Integer> order = new CopyOnWriteArrayList<Integer>();
        Thread second = Thread.ofVirtual().start(() -> coordinator.runSerial(source, 2, () -> order.add(2)));
        await().atMost(Duration.ofSeconds(2)).until(() -> second.getState() == Thread.State.TIMED_WAITING);
        coordinator.runSerial(LocalId.of("261969732607936"), 1, () -> order.add(0));
        assertEquals(List.of(0), order);
        coordinator.runSerial(source, 1, () -> order.add(1));
        assertTrue(second.join(Duration.ofSeconds(2)));
        assertEquals(List.of(0, 1, 2), order);
    }

    @Test
    void reports_gap_and_propagates_failure_without_stalling_source(CapturedOutput output) {
        SourceSerialCowCoordinator coordinator = new SourceSerialCowCoordinator(Duration.ofMillis(100));
        LocalId source = LocalId.of("263956507357120");
        IllegalStateException failure = new IllegalStateException("target failed");
        coordinator.runSerial(source, 1, () -> {
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> coordinator.runSerial(source, 3, () -> {
                    throw failure;
                })));
        assertTrue(output.getAll().contains("ORDER_GAP sourceLocalId=" + source.value() + " expected=2 actual=3"));
        ArrayList<Integer> visited = new ArrayList<Integer>();
        coordinator.runSerial(source, 4, () -> visited.add(4));
        assertEquals(List.of(4), visited);
    }
}
