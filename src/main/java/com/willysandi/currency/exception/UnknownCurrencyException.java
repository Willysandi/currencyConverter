package com.willysandi.currency.exception;

public class UnknownCurrencyException extends RuntimeException {

    public UnknownCurrencyException(String code) {
        super("Unsupported currency code: " + code);
    }
}