package sk.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sk.workingset.application.FailureReason;

/** T4 §5.4: numeric API codes are stable, unique, and assigned to fixed ranges. */
class WorkingsetResultCodeContractTest {

    @Test
    void codesAreUniqueAndInsideDeclaredPublicRanges() {
        var values = WorkingsetResultCode.values();
        var codes = Arrays.stream(values).map(WorkingsetResultCode::getCode).toList();
        assertEquals(codes.size(), new HashSet<>(codes).size(), "duplicate public error code");
        assertTrue(codes.stream().allMatch(code -> code >= 1000 && code <= 1499),
                "public error code outside 1000-1499");
    }

    @Test
    void nineApplicationCategoriesUseFixed1101To1109() {
        Map<FailureReason, Integer> expected = Map.of(
                FailureReason.NOT_FOUND, 1101,
                FailureReason.NOT_INCLUDED, 1102,
                FailureReason.DENIED, 1103,
                FailureReason.DELETED, 1104,
                FailureReason.HISTORY_UNAVAILABLE, 1105,
                FailureReason.PREPARING, 1106,
                FailureReason.CAPABILITY_NOT_SUPPORTED, 1107,
                FailureReason.DEPENDENCY_UNAVAILABLE, 1108,
                FailureReason.UNKNOWN, 1109);
        for (var reason : FailureReason.values()) {
            assertEquals(expected.get(reason), reason.resultCode().getCode(), reason.name());
        }
    }

    @Test
    void representativeCodesStayInTheirContractRanges() {
        assertRange(WorkingsetResultCode.BAD_REQUEST, 1000, 1099);
        assertRange(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT, 1000, 1099);
        assertRange(WorkingsetResultCode.WORKING_SET_NOT_EXISTS, 1200, 1299);
        assertRange(WorkingsetResultCode.TRACKER_HAS_ITEMS, 1200, 1299);
        assertRange(WorkingsetResultCode.MERGE_PAIR_MERGE_IN_PROGRESS, 1300, 1399);
        assertRange(WorkingsetResultCode.MERGE_STALE_EVIDENCE, 1300, 1399);
        assertRange(WorkingsetResultCode.MATERIALIZATION_REJECTED, 1400, 1499);
        assertRange(WorkingsetResultCode.LOCK_VERSION_CONFLICT, 1400, 1499);
    }

    private static void assertRange(WorkingsetResultCode code, int from, int to) {
        assertTrue(code.getCode() >= from && code.getCode() <= to,
                code.name() + " -> " + code.getCode() + " outside " + from + "-" + to);
    }
}
