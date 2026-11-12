package sk.workingset.application;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import sk.workingset.domain.LocalId;

@ExtendWith(OutputCaptureExtension.class)
class SourceSerialCowCoordinatorTest {
    @Test
    void queued_tasks_run_by_sequence_while_another_source_can_progress() throws InterruptedException {
        var coordinator = new SourceSerialCowCoordinator();
        LocalId source = LocalId.of("ordered");
        var order = new CopyOnWriteArrayList<Integer>();
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);

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
            coordinator.runSerial(LocalId.of("other"), 1, () -> order.add(0));
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
    void reports_gap_and_propagates_failure_without_stalling_source(CapturedOutput output) {
        var coordinator = new SourceSerialCowCoordinator();
        LocalId source = LocalId.of("gap");
        var failure = new IllegalStateException("target failed");
        coordinator.runSerial(source, 1, () -> {});
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> coordinator.runSerial(source, 3, () -> { throw failure; })));
        assertTrue(output.getAll().contains("ORDER_GAP sourceLocalId=gap expected=2 actual=3"));
        var visited = new java.util.ArrayList<Integer>();
        coordinator.runSerial(source, 4, () -> visited.add(4));
        assertEquals(List.of(4), visited);
    }
}
