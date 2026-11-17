package sk.common.error;

public class AppException extends APIException {
    private static final long serialVersionUID = 1L;

    private final ResCode resCode;

    public AppException(ResCode resCode) {
        this(resCode, (Object[]) null);
    }

    public AppException(ResCode resCode, Object... args) {
        this(resCode, formatMessage(resCode.getDesc(), args), args);
    }

    protected AppException(ResCode resCode, String message, Object[] args) {
        super(resCode.getCode(), message, args);
        this.resCode = resCode;
    }

    public ResCode getResCode() {
        return resCode;
    }
}
