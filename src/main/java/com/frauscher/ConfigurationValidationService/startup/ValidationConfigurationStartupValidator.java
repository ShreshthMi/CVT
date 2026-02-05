package com.frauscher.ConfigurationValidationService.startup;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

@Component
public class ValidationConfigurationStartupValidator {

    private final ValidationConfigurationLoader loader;

    public ValidationConfigurationStartupValidator(
            ValidationConfigurationLoader loader) {
        this.loader = loader;
    }

    @PostConstruct
    public void validateAtStartup() {
        // fail fast - any validation related exception here stops application startup
        loader.loadConfiguredRules();
    }
}
