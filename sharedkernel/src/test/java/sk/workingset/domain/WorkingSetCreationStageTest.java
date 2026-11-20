package sk.workingset.domain;

import org.junit.jupiter.api.Test;
import sk.workingset.domain.workingset.WorkingSetCreation;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static sk.workingset.domain.workingset.WorkingSetCreation.ItemOutcome.Succeeded;
import static sk.workingset.domain.workingset.WorkingSetCreation.Stage.*;

class WorkingSetCreationStageTest {
    @Test
    void createsChildAndCopiesEverySelectedTrackerBeforeBindingAnySpace() {
        var now = Instant.EPOCH;
        var request = new WorkingSetCreation.Request("1", "2", "range", List.of("10", "11"),
                List.of("30"), List.of("20", "21"));
        var task = WorkingSetCreation.submit("3", request, "4", now).start(now);

        assertEquals(CREATE_WORKING_SET, task.stage());
        assertEquals("2", task.currentItem());
        task = task.advance(Succeeded, null, now);
        assertEquals(COPY_UNSHARED_TRACKERS, task.stage());
        assertEquals("20", task.currentItem());
        task = task.advance(Succeeded, null, now);
        assertEquals(COPY_UNSHARED_TRACKERS, task.stage());
        assertEquals("21", task.currentItem());
        task = task.advance(Succeeded, null, now);
        assertEquals(BIND_PRODUCT_AND_TRACKER, task.stage());
        assertEquals("2", task.currentItem());
        assertEquals(INDEX_UNSHARED_TRACKERS, task.recordBindingResult(true, List.of(), now).stage());
    }

}
