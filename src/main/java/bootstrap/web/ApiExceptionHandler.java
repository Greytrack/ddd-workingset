package bootstrap.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import alm.requirement.domain.LockVersionConflictException;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.BindingFailure;

/**
 * 最小 REST 错误映射：保留 NotFound 与 NotIncluded 的区分，乐观锁冲突显式返回 409。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApplicationFailure.class)
    public ResponseEntity<Map<String, Object>> handleApplicationFailure(ApplicationFailure failure) {
        int status = failure.reason() == FailureReason.NOT_FOUND ? 404 : 403;
        return ResponseEntity.status(status).body(Map.of(
                "reason", failure.reason().name(),
                "message", failure.getMessage()));
    }

    @ExceptionHandler(BindingFailure.class)
    public ResponseEntity<Map<String, Object>> handleBindingFailure(BindingFailure failure) {
        int status = switch (failure.reason()) {
            case WorkingSetNotFound, ProductSpaceNotFound, SourceTrackerNotFound, TrackerNotFound,
                    TrackerBindingNotFound, SpaceBindingNotFound, SourceItemNotFound -> 404;
            case SharingModeMismatch, TrackerOutsideSlot, SourceItemMismatch -> 422;
            default -> 409;
        };
        Map<String, Object> body = new LinkedHashMap<>(failure.context());
        body.put("reason", failure.reason().name());
        body.put("message", failure.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation() {
        return ResponseEntity.status(409).body(Map.of("reason", "DATA_INTEGRITY_CONFLICT",
                "message", "The write conflicts with current persisted data"));
    }

    @ExceptionHandler(LockVersionConflictException.class)
    public ResponseEntity<Map<String, Object>> handleLockVersionConflict(LockVersionConflictException conflict) {
        return ResponseEntity.status(409).body(Map.of(
                "reason", "LOCK_VERSION_CONFLICT",
                "message", conflict.getMessage(),
                "expectedLockVersion", conflict.expected().value(),
                "actualLockVersion", conflict.actual().value()));
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException failure) {
        return ResponseEntity.badRequest().body(Map.of(
                "reason", "BAD_REQUEST",
                "message", failure.getMessage() == null ? "invalid request" : failure.getMessage()));
    }
}
