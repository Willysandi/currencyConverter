package com.willysandi.currency.exception;

public class UpstreamApiException extends RuntimeException {

    public UpstreamApiException(String message) {
        super(message);
    }

    public UpstreamApiException(String message, Throwable cause) {
        super(message, cause);
    }
}