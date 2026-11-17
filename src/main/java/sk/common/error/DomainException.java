package sk.common.error;

import java.util.List;
import java.util.Objects;

public class DomainException extends APIException {
    private static final long serialVersionUID = 1L;

    private final DomainErrorType type;
    private final ResCode resCode;
    private List<FieldViolation> fieldViolations = List.of();

    public DomainException(DomainResCode code, Object... args) {
        this(code.getType(), code, args);
    }

    public DomainException(DomainResCode code, Throwable cause, Object... args) {
        this(code.getType(), code, cause, args);
    }

    public DomainException(DomainErrorType type, ResCode code, Object... args) {
        this(type, code, formatMessage(code.getDesc(), args), args);
    }

    public DomainException(DomainErrorType type, ResCode code, Throwable cause, Object... args) {
        this(type, code, args);
        initCause(cause);
    }

    protected DomainException(DomainResCode code, String message, Object[] args) {
        this(code.getType(), code, message, args);
    }

    private DomainException(DomainErrorType type, ResCode code, String message, Object[] args) {
        super(code.getCode(), message, args == null ? new Object[0] : args);
        this.type = Objects.requireNonNull(type, "type");
        this.resCode = code;
    }

    public DomainErrorType getType() {
        return type;
    }

    public ResCode getResCode() {
        return resCode;
    }

    public DomainException withFieldViolations(List<FieldViolation> violations) {
        fieldViolations = violations == null ? List.of() : List.copyOf(violations);
        return this;
    }

    public List<FieldViolation> getFieldViolations() {
        return fieldViolations;
    }
}
