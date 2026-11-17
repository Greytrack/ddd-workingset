package bootstrap.web;

import sk.common.error.APIException;
import sk.common.error.AppException;
import sk.common.error.WorkingsetResultCode;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import alm.requirement.domain.LockVersionConflictException;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.merge.MergeFailure;

/**
 * REST 错误映射：保留 NotFound 与 NotIncluded 的区分，乐观锁冲突显式返回 409。
 *
 * <p>响应中的 {@code code}/{@code reason} 属于 T4 §5.4 API 契约 v1：{@code code} 稳定、
 * {@code reason} 稳定，{@code message} 仅用于展示；HTTP 状态按端点映射，不替代错误码。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApplicationFailure.class)
    public ResponseEntity<Map<String, Object>> handleApplicationFailure(ApplicationFailure failure) {
        int status = switch (failure.reason()) {
            case NOT_FOUND -> 404;
            case NOT_INCLUDED, DENIED -> 403;
            case DELETED -> 410;
            case HISTORY_UNAVAILABLE, PREPARING -> 409;
            case CAPABILITY_NOT_SUPPORTED -> 422;
            case DEPENDENCY_UNAVAILABLE, UNKNOWN -> 503;
        };
        return ResponseEntity.status(status).body(Map.of(
                "code", failure.getCode(), "reason", failure.reason().name(),
                "message", failure.getMessage()));
    }

    @ExceptionHandler(MergeFailure.class)
    public ResponseEntity<Map<String, Object>> handleMergeFailure(MergeFailure failure) {
        int status = switch (failure.reason()) {
            case RoundNotFound -> 404;
            case InvalidDecision, Rejected -> 422;
            default -> 409;
        };
        return ResponseEntity.status(status).body(Map.of("code", failure.getCode(), "reason", failure.reason().name(),
                "message", failure.getMessage() == null ? failure.reason().name() : failure.getMessage()));
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
        body.put("code", failure.getCode());
        body.put("reason", failure.reason().name());
        body.put("message", failure.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation() {
        return ResponseEntity.status(409).body(Map.of("code", WorkingsetResultCode.DATA_INTEGRITY_CONFLICT.getCode(),
                "reason", "DATA_INTEGRITY_CONFLICT", "message", WorkingsetResultCode.DATA_INTEGRITY_CONFLICT.getDesc()));
    }

    @ExceptionHandler(LockVersionConflictException.class)
    public ResponseEntity<Map<String, Object>> handleLockVersionConflict(LockVersionConflictException conflict) {
        return ResponseEntity.status(409).body(Map.of(
                "code", conflict.getCode(),
                "reason", "LOCK_VERSION_CONFLICT",
                "message", conflict.getMessage(),
                "expectedLockVersion", conflict.expected().value(),
                "actualLockVersion", conflict.actual().value()));
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<Map<String, Object>> handleCodedFailure(APIException failure) {
        var resultCode = failure instanceof AppException app ? app.getResCode() : null;
        int status = switch (resultCode) {
            case WorkingsetResultCode.BAD_REQUEST -> 400;
            case WorkingsetResultCode.WORKING_SET_NOT_EXISTS -> 404;
            case WorkingsetResultCode.MATERIALIZATION_REJECTED -> 409;
            case null, default -> 500;
        };
        String reason = resultCode instanceof Enum<?> value ? value.name() : "API_ERROR";
        String message = status == 500 || failure.getMessage() == null
                ? "Unexpected error" : failure.getMessage();
        return ResponseEntity.status(status).body(Map.of(
                "code", failure.getCode(), "reason", reason, "message", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException failure) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", WorkingsetResultCode.BAD_REQUEST.getCode(),
                "reason", "BAD_REQUEST",
                "message", failure.getMessage() == null ? "invalid request" : failure.getMessage()));
    }
}
