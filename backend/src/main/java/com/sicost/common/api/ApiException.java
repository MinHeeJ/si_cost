package com.sicost.common.api;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;
    private final Object details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null, null);
    }
    public ApiException(int status, String code, String message, String field, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.details = details;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public Object details() { return details; }
}
