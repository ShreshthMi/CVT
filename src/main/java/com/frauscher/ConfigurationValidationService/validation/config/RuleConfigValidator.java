package com.frauscher.ConfigurationValidationService.validation.config;

import java.util.List;

import com.frauscher.ConfigurationValidationService.model.RuleConfig;

public interface RuleConfigValidator {

    void validate(List<RuleConfig> ruleConfigs);
}