package com.saeid;

public enum HttpStatus {
    OK(200, "OK"),
    CREATED(201, "Created"),
    NOT_FOUND(404, "Not Found");

    private static final HttpStatus[] VALUES;

    static {
        VALUES = values();
    }

    private final int value;
    private final String reason;

    HttpStatus(int value, String reason) {
        this.value = value;
        this.reason = reason;
    }

    public int getValue() {
        return value;
    }

    public String getReason() {
        return reason;
    }

    public static HttpStatus valueOf(int statusCode) {
        for (HttpStatus status : values()) {
            if (status.value == statusCode) {
                return status;
            }
        }
        return null;
    }


    @Override
    public String toString() {
        return this.value +" "+this.reason;
    }
}
