package com.airedgesoft.lib.exception;

public record FieldViolation(String field, ResCode code, Object[] args) {
    public FieldViolation {
        args = args == null ? new Object[0] : args.clone();
    }

    public static FieldViolation of(String field, ResCode code, Object... args) {
        return new FieldViolation(field, code, args);
    }

    @Override
    public Object[] args() {
        return args.clone();
    }
}
