package com.frauscher.ConfigurationValidationService.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuleConfigurationException extends ConfigValidationException {

    private static final long serialVersionUID = 1L;

    public RuleConfigurationException(String message) {
        super("RULE_CONFIGURATION_ERROR", message);
        log.error("RULE_CONFIGURATION_ERROR", message);
    }

    public RuleConfigurationException(String message, Throwable cause) {
        super("RULE_CONFIGURATION_ERROR", message);
        log.error("RULE_CONFIGURATION_ERROR", message, cause);
    }
}