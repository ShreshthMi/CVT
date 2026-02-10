package com.frauscher.ConfigurationValidationService.validation;

import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.model.ValidationResult;

public final class ValidationResultFactory {

    private ValidationResultFactory() {}

    public static ValidationResult create(
            ParsedConfigFile file,
            RuleConfig rule,
            String expected,
            String actual,
            ValidationStatus status
    ) {
        return new ValidationResult(
                file.getFileName(),
                rule.getRuleType(),
                rule.getConfigBlockName(),
                rule.getConfigEntryKey(),
                expected,
                actual,
                status.name()
        );
    }
}