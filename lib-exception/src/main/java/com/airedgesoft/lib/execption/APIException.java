package com.airedgesoft.lib.execption;

import java.util.IllegalFormatException;
import java.util.Locale;

public class APIException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;
    private final Object[] args;

    public APIException(int code, String message, Object[] args) {
        super(message);
        this.code = code;
        this.args = args == null ? null : args.clone();
    }

    protected static String formatMessage(String template, Object[] args) {
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException invalidTemplate) {
            return template;
        }
    }

    public int getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args == null ? null : args.clone();
    }
}
