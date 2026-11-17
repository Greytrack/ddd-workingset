package sk.common.error;

import java.util.IllegalFormatException;
import java.util.Locale;

public class AppException extends APIException {
    private static final long serialVersionUID = 1L;

    private final ResCode resCode;

    public AppException(ResCode resCode) {
        this(resCode, (Object[]) null);
    }

    public AppException(ResCode resCode, Object... args) {
        this(resCode, format(resCode.getDesc(), args), args);
    }

    protected AppException(ResCode resCode, String message, Object[] args) {
        super(resCode.getCode(), message, args);
        this.resCode = resCode;
    }

    public ResCode getResCode() {
        return resCode;
    }

    private static String format(String template, Object[] args) {
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException invalidTemplate) {
            return template;
        }
    }
}
