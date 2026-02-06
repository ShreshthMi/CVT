package com.frauscher.ConfigurationValidationService.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidationEngineException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public ValidationEngineException(String message) {
        super("ENGINE_ERROR", message);
        log.error("ENGINE_ERROR", message);
    }

    public ValidationEngineException(String message, Throwable cause) {
        super("ENGINE_ERROR", message);
        initCause(cause);
        log.error("ENGINE_ERROR", message, cause);
    }
}
