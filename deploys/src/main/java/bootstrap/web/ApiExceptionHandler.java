package bootstrap.web;

import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.lib.execption.APIException;
import com.airedgesoft.lib.execption.AppException;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.lib.execption.ResCode;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import com.airedgesoft.sharekernel.workingset.error.MergeResCode;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * REST 错误映射：保留 NotFound 与 NotIncluded 的区分，乐观锁冲突显式返回 409。
 *
 * <p>响应中的 {@code code}/{@code reason} 属于 T4 §5.4 API 契约 v1：{@code code} 稳定、
 * {@code reason} 稳定，{@code message} 仅用于展示；HTTP 状态按端点映射，不替代错误码。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation() {
        return ResponseEntity.status(409).body(Map.of("code", WorkingsetResultCode.DATA_INTEGRITY_CONFLICT.getCode(),
                "reason", "DATA_INTEGRITY_CONFLICT", "message", WorkingsetResultCode.DATA_INTEGRITY_CONFLICT.getDesc()));
    }

    @ExceptionHandler(LockVersionConflictException.class)
    public ResponseEntity<?> handleLockVersionConflict(LockVersionConflictException conflict) {
        return domainResponse(conflict, Map.of(
                "code", conflict.getCode(),
                "reason", "LOCK_VERSION_CONFLICT",
                "message", conflict.getMessage(),
                "expectedLockVersion", conflict.expected().value(),
                "actualLockVersion", conflict.actual().value()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<?> handleDomainFailure(DomainException failure) {
        ResCode resultCode = failure.getResCode();
        String reason = switch (resultCode) {
            case BindingResCode binding -> binding.reason();
            case MergeResCode merge -> merge.reason();
            case Enum<?> value -> value.name();
            default -> failure.getType().name();
        };
        Map<String, Object> body = new LinkedHashMap<>(failure.getContext());
        body.put("code", failure.getCode());
        body.put("reason", reason);
        String fallbackMessage = resultCode instanceof MergeResCode ? reason : "Unexpected error";
        body.put("message", failure.getType().httpStatus() >= 500 ? "Unexpected error"
                : Objects.toString(failure.getMessage(), fallbackMessage));
        return domainResponse(failure, body);
    }

    private ResponseEntity<?> domainResponse(DomainException failure, Map<String, Object> body) {
        int status = failure.getType().httpStatus();
        if (failure.getFieldViolations().isEmpty()) {
            return ResponseEntity.status(status).body(body);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), (String) body.get("message"));
        body.forEach(problem::setProperty);
        problem.setProperty("fieldViolations", failure.getFieldViolations().stream()
                .map(violation -> Map.of("field", violation.field(), "code", violation.code().getCode(),
                        "args", violation.args()))
                .toList());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<Map<String, Object>> handleCodedFailure(APIException failure) {
        ResCode resultCode = failure instanceof AppException app ? app.getResCode() : null;
        int status = switch (resultCode) {
            case WorkingsetResultCode.BAD_REQUEST -> 400;
            case BindingResCode.WORKING_SET_NOT_EXISTS -> 404;
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
