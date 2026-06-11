package com.willysandi.currency.api;

import com.willysandi.currency.exception.UnknownCurrencyException;
import com.willysandi.currency.exception.UpstreamApiException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(String error) {
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return new ApiError(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return new ApiError("Invalid value for parameter '" + e.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingParam(MissingServletRequestParameterException e) {
        return new ApiError("Missing required parameter '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(UnknownCurrencyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleUnknownCurrency(UnknownCurrencyException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(UpstreamApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiError handleUpstream(UpstreamApiException e) {
        return new ApiError("Exchange rate provider is currently unavailable");
    }
}