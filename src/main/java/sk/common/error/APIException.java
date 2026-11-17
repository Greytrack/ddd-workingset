package sk.common.error;

public class APIException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;
    private final Object[] args;

    public APIException(int code, String message, Object[] args) {
        super(message);
        this.code = code;
        this.args = args == null ? null : args.clone();
    }

    public int getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args == null ? null : args.clone();
    }
}
