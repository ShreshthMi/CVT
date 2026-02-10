package com.frauscher.ConfigurationValidationService.validation;

import java.util.List;

import com.frauscher.ConfigurationValidationService.model.ValidationResult;
import com.frauscher.ConfigurationValidationService.validation.context.RuleExecutionContext;

public interface ValidationRule {

    RuleType supportedType();

    List<ValidationResult> execute(RuleExecutionContext context);
}