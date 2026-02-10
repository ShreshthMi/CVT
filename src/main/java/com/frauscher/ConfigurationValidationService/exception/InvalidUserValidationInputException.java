package com.frauscher.ConfigurationValidationService.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InvalidUserValidationInputException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public InvalidUserValidationInputException(String message) {
        super("INVALID_USER_INPUT", message);
        log.error("INVALID_USER_INPUT", message);
    }
}