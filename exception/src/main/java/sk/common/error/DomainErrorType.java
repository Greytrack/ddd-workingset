package sk.common.error;

/** Framework-independent mapping of error semantics to HTTP status. */
public enum DomainErrorType {
    INVALID_ARGUMENT(400), NOT_FOUND(404), CONFLICT(409), UNPROCESSABLE_ENTITY(422),
    UNAUTHORIZED(401), FORBIDDEN(403), GONE(410), ABORTED(409), ALREADY_EXISTS(409),
    FAILED_PRECONDITION(400), RESOURCE_EXHAUSTED(429), OUT_OF_RANGE(400), INTERNAL(500),
    DATA_LOSS(500), UNAVAILABLE(503), DEADLINE_EXCEEDED(504), UNIMPLEMENTED(501);

    private final int httpStatus;

    DomainErrorType(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
