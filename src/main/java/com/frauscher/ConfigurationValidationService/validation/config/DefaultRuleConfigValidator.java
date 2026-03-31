package com.frauscher.ConfigurationValidationService.validation.config;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.frauscher.ConfigurationValidationService.constants.ValidationConstants;
import com.frauscher.ConfigurationValidationService.exception.RuleConfigurationException;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.validation.ConfigFileMarker;
import com.frauscher.ConfigurationValidationService.validation.RuleOrigin;
import com.frauscher.ConfigurationValidationService.validation.RuleType;

@Component
public class DefaultRuleConfigValidator implements RuleConfigValidator {

    @Override
    public void validate(List<RuleConfig> ruleConfigs) {
        validateRuleDefinitions(ruleConfigs);
        validateDuplicates(ruleConfigs);
    }

    private void validateRuleDefinitions(List<RuleConfig> ruleConfigs) {

        for (RuleConfig ruleConfig : ruleConfigs) {

            normalize(ruleConfig);
            validateMandatoryFields(ruleConfig);
            validateUiInputRequired(ruleConfig);
            validateOptionalFields(ruleConfig);
            validateRuleSpecifics(ruleConfig);
        }
    }

    private void normalize(RuleConfig ruleConfig) {

        ruleConfig.setRuleType(trimToNull(ruleConfig.getRuleType()));
        ruleConfig.setConfigBlockName(trimToNull(ruleConfig.getConfigBlockName()));
        ruleConfig.setConfigEntryKey(trimToNull(ruleConfig.getConfigEntryKey()));
        ruleConfig.setUiInputRequired(trimToNull(ruleConfig.getUiInputRequired()));
        ruleConfig.setValidateOnlyInFilesWith(
                trimToNull(ruleConfig.getValidateOnlyInFilesWith()));
        ruleConfig.setDefaultValue(trimToNull(ruleConfig.getDefaultValue()));
    }

    private void validateMandatoryFields(RuleConfig ruleConfig) {

        if (ruleConfig.getRuleType() == null) {
            throw new RuleConfigurationException("ruleType is mandatory");
        }

        if (ruleConfig.getConfigBlockName() == null) {
            throw new RuleConfigurationException(
                    "ConfigBlockName is mandatory for ruleType " +
                            ruleConfig.getRuleType()
            );
        }

        if (ruleConfig.getConfigEntryKey() == null) {
            throw new RuleConfigurationException(
                    "ConfigEntryKey is mandatory for ruleType " +
                            "(ruleType=" + ruleConfig.getRuleType() +
                            ", block=" + ruleConfig.getConfigBlockName() + ")"
            );
        }

        RuleType.fromExternal(ruleConfig.getRuleType());
    }

    private void validateUiInputRequired(RuleConfig ruleConfig) {

        if (ruleConfig.getOrigin() == RuleOrigin.CONFIGURED &&
                ruleConfig.getUiInputRequired() == null) {

            throw new RuleConfigurationException(
                    "UIInputRequired is mandatory for configured rule " +
                            "(ruleType=" + ruleConfig.getRuleType() +
                            ", block=" + ruleConfig.getConfigBlockName() +
                            ", entry=" + ruleConfig.getConfigEntryKey() + ")"
            );
        }
    }

    private void validateOptionalFields(RuleConfig ruleConfig) {

        String marker = ruleConfig.getValidateOnlyInFilesWith();

        if (marker == null) {
            return; // optional field
        }

        if (!ConfigFileMarker.isValid(marker)) {
            throw new RuleConfigurationException(
                    "Invalid ValidateOnlyInFilesWith value '" + marker +
                            "'. Allowed values: " +
                            java.util.Arrays.toString(ConfigFileMarker.values())
            );
        }
    }


    private void validateRuleSpecifics(RuleConfig ruleConfig) {

        RuleType type = RuleType.fromExternal(ruleConfig.getRuleType());

        switch (type) {

            case RANGE_CHECK -> validateRangeCheck(ruleConfig);

            case DUPLICATE_CHECK -> validateDuplicateCheck(ruleConfig);

            case INPUT_MATCH_OR_BLOCK_NOT_FOUND -> validateDefaultValue(ruleConfig);

            case INPUT_MATCH,
                 OPTIONAL_INPUT_MATCH,
                 MULTIPLE_BLOCK_SINGLE_INPUT_MATCH,
                 MULTIPLE_BLOCK_MULTIPLE_INPUT_MATCH,
                 PROJECT_BLOCK_CHECK -> {
                // no extra validation
            }
        }
    }


    private void validateDuplicateCheck(RuleConfig ruleConfig) {

        if ("Yes".equalsIgnoreCase(ruleConfig.getUiInputRequired())) {
            throw new RuleConfigurationException(
                    "UIInputRequired must be NO for DuplicateCheck rule " +
                            "(block=" + ruleConfig.getConfigBlockName() +
                            ", entry=" + ruleConfig.getConfigEntryKey() + ")"
            );
        }
    }


    private void validateRangeCheck(RuleConfig ruleConfig) {

        Integer min = ruleConfig.getMin();
        Integer max = ruleConfig.getMax();

        if (ValidationConstants.YES.equalsIgnoreCase(ruleConfig.getUiInputRequired())) {
            return;
        }

        if ((min == null || max == null) ) {
            throw new RuleConfigurationException(
                    "RangeCheck requires both min and max"
            );
        }

        if (min >= max) {
            throw new RuleConfigurationException(
                    "RangeCheck requires min < max"
            );
        }
    }

    private void validateDefaultValue(RuleConfig ruleConfig) {

        if (ruleConfig.getDefaultValue() == null) {
            throw new RuleConfigurationException(
                    "DefaultValue is mandatory for rule type " + ruleConfig.getRuleType() +
                            " (block=" + ruleConfig.getConfigBlockName() +
                            ", entry=" + ruleConfig.getConfigEntryKey() + ")"
            );
        }
    }

    private void validateDuplicates(List<RuleConfig> ruleConfigs) {

        Set<String> seen = new HashSet<>();

        for (RuleConfig rule : ruleConfigs) {

            String key =
                    rule.getRuleType() + "::" +
                            rule.getConfigBlockName() + "::" +
                            rule.getConfigEntryKey();

            if (!seen.add(key)) {
                throw new RuleConfigurationException(
                        "Duplicate rule detected: " + key
                );
            }
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
