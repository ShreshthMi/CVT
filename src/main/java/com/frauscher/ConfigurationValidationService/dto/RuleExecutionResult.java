package com.frauscher.ConfigurationValidationService.dto;

import java.util.List;

import com.frauscher.ConfigurationValidationService.model.ValidationResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RuleExecutionResult {

    private final List<ValidationResult> results;
    private final boolean ruleExecuted;
}
