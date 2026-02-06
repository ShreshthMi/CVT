package com.frauscher.ConfigurationValidationService.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ConfigValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    protected ConfigValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        log.error(message, errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }
}