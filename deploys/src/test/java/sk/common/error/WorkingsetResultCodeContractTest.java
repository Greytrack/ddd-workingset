package sk.common.error;

import org.springframework.http.ResponseEntity;
import alm.requirement.domain.RequirementResultCode;
import bootstrap.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import sk.workingset.application.FailureReason;
import sk.workingset.error.BindingResCode;
import sk.workingset.error.MergeResCode;
import sk.workingset.error.WorkingsetResultCode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** T4 §5.4: numeric API codes are stable, unique, and assigned to fixed ranges. */
class WorkingsetResultCodeContractTest {

    @Test
    void bindingFailureResponseKeepsItsPublicReasonAndContext() {
        DomainException error = DomainException.withMessage(BindingResCode.WORKING_SET_HAS_CHILDREN,
                "42", Map.of("childWorkingSetIds", java.util.List.of("43")));

        ResponseEntity<?> response = new ApiExceptionHandler().handleDomainFailure(error);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(Map.of("code", 1207, "reason", "WorkingSetHasChildren", "message", "42",
                "childWorkingSetIds", java.util.List.of("43")), response.getBody());
    }

    @Test
    void mergeFailureResponseKeepsItsPublicReasonAndFallbackMessage() {
        DomainException error = DomainException.withMessage(MergeResCode.STALE_EVIDENCE, null);

        ResponseEntity<?> response = new ApiExceptionHandler().handleDomainFailure(error);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(Map.of("code", 1306, "reason", "StaleEvidence", "message", "StaleEvidence"), response.getBody());
    }

    @Test
    void domainErrorKeepsBusinessCodeWhenStatusIsOverridden() {
        IllegalStateException cause = new IllegalStateException("original failure");
        DomainException error = new DomainException(DomainErrorType.CONFLICT, WorkingsetResultCode.BAD_REQUEST,
                cause, "invalid title");

        assertEquals(1001, error.getCode());
        assertEquals(409, error.getType().httpStatus());
        assertEquals("invalid title", error.getMessage());
        assertSame(cause, error.getCause());
    }

    @Test
    void codesAreUniqueAndInsideDeclaredPublicRanges() {
        java.util.List<Integer> codes = java.util.stream.Stream.of(WorkingsetResultCode.values(),
                        BindingResCode.values(), MergeResCode.values(), RequirementResultCode.values())
                .flatMap(Arrays::stream).map(DomainResCode::getCode).toList();
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
        for (FailureReason reason : FailureReason.values()) {
            assertEquals(expected.get(reason), reason.resultCode().getCode(), reason.name());
        }
    }

    @Test
    void representativeCodesStayInTheirContractRanges() {
        assertRange(WorkingsetResultCode.BAD_REQUEST, 1000, 1099);
        assertRange(WorkingsetResultCode.DATA_INTEGRITY_CONFLICT, 1000, 1099);
        for (BindingResCode code : BindingResCode.values()) {
            assertRange(code, 1200, 1299);
        }
        for (MergeResCode code : MergeResCode.values()) {
            assertRange(code, 1300, 1399);
        }
        assertRange(WorkingsetResultCode.MATERIALIZATION_REJECTED, 1400, 1499);
        assertEquals(1403, RequirementResultCode.LOCK_VERSION_CONFLICT.getCode());
    }

    private static void assertRange(DomainResCode code, int from, int to) {
        assertTrue(code.getCode() >= from && code.getCode() <= to,
                code + " -> " + code.getCode() + " outside " + from + "-" + to);
    }
}
